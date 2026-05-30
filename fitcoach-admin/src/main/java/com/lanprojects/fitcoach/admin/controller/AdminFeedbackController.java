package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.BatchUpdateFeedbackStatusRequest;
import com.lanprojects.fitcoach.admin.dto.FeedbackDetailDto;
import com.lanprojects.fitcoach.admin.dto.FeedbackSummaryDto;
import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.UpdateFeedbackStatusRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.admin.service.AdminFeedbackService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.BatchOperationResult;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.util.CsvHttpResponseUtil;
import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import com.lanprojects.fitcoach.feedback.entity.FeedbackType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 反馈管理（admin 后台）。
 * <p>路径前缀：/api/admin/feedbacks
 * <ul>
 *   <li>{@code GET /} —— 分页列表，支持 status/type/keyword/start/end 多维过滤</li>
 *   <li>{@code GET /{id}} —— 详情</li>
 *   <li>{@code PUT /{id}/status} —— 状态流转 + 处理回复</li>
 * </ul>
 */
@Slf4j
@Tag(name = "后台-反馈管理", description = "用户反馈列表/详情/状态流转 + 处理回复")
@RestController
@RequestMapping("/api/admin/feedbacks")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final AdminFeedbackService adminFeedbackService;
    private final AdminAuditLogService auditLogService;

    /**
     * 分页查询反馈
     *
     * @param status  状态枚举，可选（PENDING/PROCESSING/RESOLVED/IGNORED）
     * @param type    类型枚举，可选（SUGGESTION/EXPERIENCE/OTHER）
     * @param keyword content / uid 关键字
     * @param start   起始时间（毫秒，含），可选
     * @param end     结束时间（毫秒，不含），可选
     */
    @GetMapping
    public Result<PageResponse<FeedbackSummaryDto>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "start", required = false) Long start,
            @RequestParam(value = "end", required = false) Long end) {
        FeedbackStatus s = parseEnum(FeedbackStatus.class, status, ResultCode.ADMIN_FEEDBACK_STATUS_INVALID);
        FeedbackType t = parseEnum(FeedbackType.class, type, ResultCode.FEEDBACK_TYPE_INVALID);
        return Result.success(adminFeedbackService.listFeedbacks(page, size, s, t, keyword, start, end));
    }

    /** 反馈详情 */
    @GetMapping("/{id}")
    public Result<FeedbackDetailDto> detail(@PathVariable("id") Long id) {
        return Result.success(adminFeedbackService.getFeedbackDetail(id));
    }

    /** 更新状态 / 回复 */
    @PutMapping("/{id}/status")
    public Result<FeedbackDetailDto> updateStatus(HttpServletRequest request,
                                                  @PathVariable("id") Long id,
                                                  @RequestBody(required = false) UpdateFeedbackStatusRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            FeedbackDetailDto detail = adminFeedbackService.updateStatus(id, body, operator);
            String newStatus = body != null && body.getStatus() != null ? body.getStatus().name() : "(unchanged)";
            boolean hasReply = body != null && body.getHandlerReply() != null && !body.getHandlerReply().isBlank();
            auditLogService.logSuccess(request, AdminAuditAction.UPDATE_FEEDBACK_STATUS,
                    "FEEDBACK", String.valueOf(id),
                    String.format("status=%s, hasReply=%s", newStatus, hasReply));
            return Result.success(detail);
        } catch (RuntimeException e) {
            String newStatus = body != null && body.getStatus() != null ? body.getStatus().name() : "(unchanged)";
            auditLogService.logFailure(request, AdminAuditAction.UPDATE_FEEDBACK_STATUS,
                    "FEEDBACK", String.valueOf(id),
                    String.format("status=%s", newStatus), e.getMessage());
            throw e;
        }
    }

    /**
     * 批量更新反馈状态（同一目标状态、可选共用回复）。
     * <p>POST /api/admin/feedbacks/batch/status
     * <p>请求体：{ ids: [..], status: 'RESOLVED', handlerReply: '可选' }
     * <p>语义：部分成功也走 2xx；返回体 affected / missing 给前端做 toast。
     */
    @PostMapping("/batch/status")
    public Result<BatchOperationResult> batchUpdateStatus(HttpServletRequest request,
                                                          @RequestBody(required = false) BatchUpdateFeedbackStatusRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        int requested = body == null || body.getIds() == null ? 0 : body.getIds().size();
        String statusName = body != null && body.getStatus() != null ? body.getStatus().name() : "(null)";
        try {
            BatchOperationResult result = adminFeedbackService.batchUpdateStatus(body, operator);
            auditLogService.logSuccess(request, AdminAuditAction.BATCH_UPDATE_FEEDBACK_STATUS,
                    "FEEDBACK", null,
                    String.format("requested=%d, affected=%d, missing=%d, status=%s",
                            requested, result.getAffected(), result.getMissing().size(), statusName));
            return Result.success(result);
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.BATCH_UPDATE_FEEDBACK_STATUS,
                    "FEEDBACK", null,
                    String.format("requested=%d, status=%s", requested, statusName), e.getMessage());
            throw e;
        }
    }

    /**
     * P2-12：按筛选条件导出反馈 CSV（最多 10000 条，超过请进一步筛选）。
     * <p>路径：{@code GET /api/admin/feedbacks/export}
     */
    @GetMapping("/export")
    public void exportCsv(HttpServletRequest request, HttpServletResponse response,
                          @RequestParam(value = "status", required = false) String status,
                          @RequestParam(value = "type", required = false) String type,
                          @RequestParam(value = "keyword", required = false) String keyword,
                          @RequestParam(value = "start", required = false) Long start,
                          @RequestParam(value = "end", required = false) Long end) throws IOException {
        FeedbackStatus s = parseEnum(FeedbackStatus.class, status, ResultCode.ADMIN_FEEDBACK_STATUS_INVALID);
        FeedbackType t = parseEnum(FeedbackType.class, type, ResultCode.FEEDBACK_TYPE_INVALID);
        AdminFeedbackService.ExportResult result = adminFeedbackService.exportFeedbacks(s, t, keyword, start, end);
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        auditLogService.logSuccess(request, AdminAuditAction.EXPORT_FEEDBACKS, "FEEDBACK", null,
                String.format("rows=%d, status=%s, type=%s", result.rows().size(), status, type));
        log.info("导出反馈 CSV, operator={}, rows={}", operator, result.rows().size());

        CsvHttpResponseUtil.write(response, "feedbacks",
                List.of("ID", "uid", "昵称", "类型", "状态", "内容", "App 版本", "平台", "处理人", "处理回复", "创建时间", "处理时间"),
                result.rows(), fb -> List.of(
                        fb.getId() == null ? "" : String.valueOf(fb.getId()),
                        nullToEmpty(fb.getUid()),
                        nullToEmpty(result.uidToNickname().get(fb.getUid())),
                        fb.getType() == null ? "" : fb.getType().name(),
                        fb.getStatus() == null ? "" : fb.getStatus().name(),
                        nullToEmpty(fb.getContent()),
                        nullToEmpty(fb.getAppVersion()),
                        nullToEmpty(fb.getPlatform()),
                        nullToEmpty(fb.getHandlerAdmin()),
                        nullToEmpty(fb.getHandlerReply()),
                        fmtIso(fb.getCreatedAt()),
                        fmtIso(fb.getHandledAt())
                ));
    }

    // ====== 内部 ======

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String fmtIso(LocalDateTime t) {
        return t == null ? "" : t.format(ISO_FMT);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, ResultCode invalidCode) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(invalidCode, "枚举值不合法：" + raw);
        }
    }
}
