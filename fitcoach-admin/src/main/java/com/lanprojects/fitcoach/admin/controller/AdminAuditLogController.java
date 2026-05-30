package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLog;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogRepository;
import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.audit.AdminAuditLogDto;
import com.lanprojects.fitcoach.admin.entity.AdminRole;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogRepository repository;

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
