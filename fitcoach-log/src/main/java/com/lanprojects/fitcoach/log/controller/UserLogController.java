package com.lanprojects.fitcoach.log.controller;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.log.dto.LogTaskDto;
import com.lanprojects.fitcoach.log.dto.ReportFailureRequest;
import com.lanprojects.fitcoach.log.dto.UploadLogResponse;
import com.lanprojects.fitcoach.log.service.LogPullService;
import com.lanprojects.fitcoach.login.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 客户端日志上传/失败上报接口（前缀：/api/logs）。
 *
 * <p>所有接口需要 Authorization: Bearer accessToken。鉴权与 FeedbackController 同风格 ——
 * 复用 {@link AuthService#getCurrentUser}，不引入 Spring Security。
 *
 * <ul>
 *   <li>POST /api/logs/upload —— multipart 上传 zip；幂等</li>
 *   <li>POST /api/logs/tasks/{id}/fail —— 客户端主动报失败，触发回滚 PENDING 或标 FAILED</li>
 * </ul>
 *
 * <p><b>注意</b>：原"客户端拉取 pending 任务"的接口已下线（{@code GET /api/logs/pending}）。
 * 拉取动作合并到 fitcoach-clientbus 提供的通用轮询入口 {@code GET /api/client/poll}，
 * 由 {@code LogPullContribution} 通过 SPI 注入到该入口；同时心跳（{@code UserActivityService.touch}）
 * 也跟随挪到通用入口，本 controller 不再调用心跳。
 */
@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class UserLogController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final LogPullService logPullService;

    /**
     * 上传 zip。
     * <p>POST /api/logs/upload  (multipart/form-data)
     * <p>fields: file (必填), taskId (必填，form 字段)
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Result<UploadLogResponse> upload(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("taskId") Long taskId,
            @RequestParam("file") MultipartFile file) {
        String uid = currentUid(authorization);
        return Result.success(logPullService.acceptUpload(uid, taskId, file));
    }

    /**
     * 客户端主动回报上传失败。
     * <p>POST /api/logs/tasks/{id}/fail  (application/json)
     */
    @PostMapping("/tasks/{id}/fail")
    public Result<LogTaskDto> reportFailure(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") Long id,
            @RequestBody(required = false) ReportFailureRequest body) {
        String uid = currentUid(authorization);
        String reason = body == null ? null : body.getReason();
        return Result.success(logPullService.reportFailure(uid, id, reason));
    }

    // ====== 鉴权 ======

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
