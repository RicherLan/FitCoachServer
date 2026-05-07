package com.lanprojects.fitcoach.log.controller;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.log.dto.LogTaskDto;
import com.lanprojects.fitcoach.log.dto.PendingTaskDto;
import com.lanprojects.fitcoach.log.dto.ReportFailureRequest;
import com.lanprojects.fitcoach.log.dto.UploadLogResponse;
import com.lanprojects.fitcoach.log.service.LogPullService;
import com.lanprojects.fitcoach.login.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * 客户端日志拉取/上传接口（前缀：/api/logs）。
 *
 * <p>所有接口需要 Authorization: Bearer accessToken。鉴权与 FeedbackController 同风格 ——
 * 复用 {@link AuthService#getCurrentUser}，不引入 Spring Security。
 *
 * <ul>
 *   <li>GET /api/logs/pending —— 客户端 120s 轮询；命中即把任务状态原子改为 UPLOADING；</li>
 *   <li>POST /api/logs/upload —— multipart 上传 zip；幂等</li>
 *   <li>POST /api/logs/tasks/{id}/fail —— 客户端主动报失败，触发回滚 PENDING 或标 FAILED</li>
 * </ul>
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
     * 拉一条 pending 任务。
     * <p>未命中：data=null，客户端按 120s 周期下次再来；命中：data=PendingTaskDto。
     */
    @GetMapping("/pending")
    public Result<PendingTaskDto> pending(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String uid = currentUid(authorization);
        Optional<PendingTaskDto> opt = logPullService.claimNextPending(uid);
        return Result.success(opt.orElse(null));
    }

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
