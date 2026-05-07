package com.lanprojects.fitcoach.feedback.controller;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.feedback.dto.CreateFeedbackRequest;
import com.lanprojects.fitcoach.feedback.dto.FeedbackResponse;
import com.lanprojects.fitcoach.feedback.dto.UploadAttachmentResponse;
import com.lanprojects.fitcoach.feedback.service.FeedbackService;
import com.lanprojects.fitcoach.login.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 反馈接口。
 * <p>接口前缀：/api/feedback。所有接口需要 Authorization: Bearer {accessToken}。
 * <p>token 校验复用 {@link AuthService#getCurrentUser(String)}，与 UserController 保持一致风格，
 * 不引入 Spring Security 这类重武器。
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final FeedbackService feedbackService;

    /**
     * 上传单张附件
     * <p>POST /api/feedback/attachment  (multipart/form-data, field=file)
     * <br>Returns: {@link UploadAttachmentResponse}（含可访问 URL）
     * <p>客户端需先本地压缩到 1MB 以内，服务端校验 contentType + size 兜底。
     * 附件成功上传后，URL 由客户端缓存，最后随 createFeedback 一起提交。
     */
    @PostMapping(value = "/attachment", consumes = "multipart/form-data")
    public Result<UploadAttachmentResponse> uploadAttachment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file) {
        String uid = currentUid(authorization);
        return Result.success(feedbackService.uploadAttachment(uid, file));
    }

    /**
     * 创建反馈
     * <p>POST /api/feedback  (application/json)
     * <br>Body: {@link CreateFeedbackRequest}
     * <br>Returns: {@link FeedbackResponse}
     */
    @PostMapping
    public Result<FeedbackResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) CreateFeedbackRequest request) {
        String uid = currentUid(authorization);
        return Result.success(feedbackService.createFeedback(uid, request));
    }

    // ====== 鉴权辅助（与 UserController 风格一致；后续可抽到 common，本期重复 2 处可接受） ======

    private String currentUid(String authorization) {
        return authService.getCurrentUser(extractToken(authorization)).getUid();
    }

    private String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少 Authorization 请求头");
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authorization 必须以 'Bearer ' 开头");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authorization 中的 token 为空");
        }
        return token;
    }
}
