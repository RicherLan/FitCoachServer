package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLog;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogRepository;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.audit.AdminAuditLogDto;
import com.lanprojects.fitcoach.admin.entity.AdminRole;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.util.CsvHttpResponseUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 后台审计日志查询接口（admin only）。
 *
 * <p>路径：{@code GET /api/admin/audit-logs}
 * <p>权限：<b>仅 SUPER_ADMIN 可访问</b>，因为审计日志的 summary 可能包含敏感线索
 *  （如目标 uid / 订单号 / 配置 key），不应该让普通运营或 VIEWER 看到。
 *
 * <p>分页基本规则与其它后台接口对齐：1-based page、size 默认 20，最大 100、按 createdAt desc。
 *
 * <p>不提供"修改 / 删除审计日志"接口 —— 审计本身要求不可篡改。
 */
@Slf4j
@Tag(name = "后台-审计日志", description = "高危操作审计查询（仅 SUPER_ADMIN）")
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    /** 单次导出最大条数（审计日志可能很大，导出请按时间窗口精细过滤） */
    private static final int MAX_EXPORT_SIZE = 10_000;
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminAuditLogRepository repository;
    private final AdminAuditLogService auditLogService;

    /**
     * 多维筛选分页查询。
     *
     * @param page 1-based 页码（默认 1）
     * @param size 每页条数（默认 20，上限 100）
     * @param username 操作员 username 精确匹配（可选）
     * @param action 操作枚举名（可选，传错枚举名直接报参数不合法）
     * @param targetType 目标类型（可选，如 ORDER / USER / SYS_CONFIG）
     * @param targetId 目标 id（可选）
     * @param startMs 起始时间 epoch millis（含，可选）
     * @param endMs 结束时间 epoch millis（不含，可选）
     */
    @GetMapping
    public Result<PageResponse<AdminAuditLogDto>> list(
            HttpServletRequest request,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetId", required = false) String targetId,
            @RequestParam(value = "start", required = false) Long startMs,
            @RequestParam(value = "end", required = false) Long endMs) {

        // 仅 SUPER_ADMIN 可查（VIEWER 由拦截器拦了写操作，但读不被拦，这里手动卡）
        requireSuperAdmin(request);

        // 校验 action 枚举（早失败比查回 0 条更友好）
        AdminAuditAction actionEnum = null;
        if (action != null && !action.isBlank()) {
            try {
                actionEnum = AdminAuditAction.valueOf(action.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "action 不是合法枚举：" + action);
            }
        }

        int p = Math.max(page, 1) - 1;
        int s = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(p, s);

        LocalDateTime start = toLdt(startMs);
        LocalDateTime end = toLdt(endMs);

        Page<AdminAuditLog> result = repository.search(
                blankToNull(username), actionEnum,
                blankToNull(targetType), blankToNull(targetId),
                start, end, pageable);

        return Result.success(PageResponse.from(result, AdminAuditLogDto::from));
    }

    /**
     * P2-12：按筛选条件导出审计日志 CSV（最多 {@value #MAX_EXPORT_SIZE} 条；仅 SUPER_ADMIN）。
     * <p>路径：{@code GET /api/admin/audit-logs/export}
     * <p>导出动作本身也会落一条 {@link AdminAuditAction#EXPORT_AUDIT_LOGS} 审计。
     */
    @GetMapping("/export")
    public void exportCsv(HttpServletRequest request, HttpServletResponse response,
                          @RequestParam(value = "username", required = false) String username,
                          @RequestParam(value = "action", required = false) String action,
                          @RequestParam(value = "targetType", required = false) String targetType,
                          @RequestParam(value = "targetId", required = false) String targetId,
                          @RequestParam(value = "start", required = false) Long startMs,
                          @RequestParam(value = "end", required = false) Long endMs) throws IOException {
        requireSuperAdmin(request);

        AdminAuditAction actionEnum = null;
        if (action != null && !action.isBlank()) {
            try {
                actionEnum = AdminAuditAction.valueOf(action.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "action 不是合法枚举：" + action);
            }
        }

        Pageable pageable = PageRequest.of(0, MAX_EXPORT_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminAuditLog> page = repository.search(
                blankToNull(username), actionEnum,
                blankToNull(targetType), blankToNull(targetId),
                toLdt(startMs), toLdt(endMs), pageable);
        List<AdminAuditLog> rows = page.getContent();

        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        auditLogService.logSuccess(request, AdminAuditAction.EXPORT_AUDIT_LOGS, "AUDIT_LOG", null,
                String.format("rows=%d, username=%s, action=%s, targetType=%s",
                        rows.size(), username, action, targetType));
        log.info("导出审计日志 CSV, operator={}, rows={}", operator, rows.size());

        CsvHttpResponseUtil.write(response, "audit_logs",
                List.of("ID", "操作员", "角色", "操作", "目标类型", "目标ID", "成功", "摘要", "错误信息", "IP", "请求路径", "User-Agent", "时间"),
                rows, r -> List.of(
                        r.getId() == null ? "" : String.valueOf(r.getId()),
                        nullToEmpty(r.getAdminUsername()),
                        nullToEmpty(r.getAdminRole()),
                        r.getAction() == null ? "" : r.getAction().name(),
                        nullToEmpty(r.getTargetType()),
                        nullToEmpty(r.getTargetId()),
                        r.getSuccess() == null ? "" : (r.getSuccess() ? "是" : "否"),
                        nullToEmpty(r.getSummary()),
                        nullToEmpty(r.getErrorMsg()),
                        nullToEmpty(r.getIp()),
                        nullToEmpty(r.getRequestUri()),
                        nullToEmpty(r.getUa()),
                        r.getCreatedAt() == null ? "" : r.getCreatedAt().format(ISO_FMT)
                ));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static void requireSuperAdmin(HttpServletRequest request) {
        Object roleAttr = request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ROLE);
        if (roleAttr == null || !AdminRole.SUPER_ADMIN.name().equals(roleAttr.toString())) {
            throw new BusinessException(ResultCode.ADMIN_PERMISSION_DENIED, "仅超级管理员可查看审计日志");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static LocalDateTime toLdt(Long ms) {
        if (ms == null || ms <= 0) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }
}
