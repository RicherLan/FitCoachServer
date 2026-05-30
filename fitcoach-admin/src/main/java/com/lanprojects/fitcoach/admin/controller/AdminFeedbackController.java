package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.FeedbackDetailDto;
import com.lanprojects.fitcoach.admin.dto.FeedbackSummaryDto;
import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.UpdateFeedbackStatusRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.admin.service.AdminFeedbackService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import com.lanprojects.fitcoach.feedback.entity.FeedbackType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    // ====== 内部 ======

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, ResultCode invalidCode) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(invalidCode, "枚举值不合法：" + raw);
        }
    }
}
