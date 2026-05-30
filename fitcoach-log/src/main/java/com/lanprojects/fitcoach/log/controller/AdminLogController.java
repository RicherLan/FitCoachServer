package com.lanprojects.fitcoach.log.controller;

import com.lanprojects.fitcoach.common.audit.AdminAuditPort;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.log.dto.CreateLogTaskRequest;
import com.lanprojects.fitcoach.log.dto.LogTaskDto;
import com.lanprojects.fitcoach.log.entity.LogPullStatus;
import com.lanprojects.fitcoach.log.entity.LogPullTask;
import com.lanprojects.fitcoach.log.service.LogPullService;
import com.lanprojects.fitcoach.log.service.LogStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * admin 日志任务管理接口（前缀：/api/admin/logs/tasks）。
 *
 * <p>所有接口走 {@code AdminAuthInterceptor}（在 fitcoach-admin 模块注册），
 * controller 层只关心业务，不重复鉴权代码。{@code admin.username} 从 request attribute 取。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/logs/tasks")
public class AdminLogController {

    /** 与 AdminAuthInterceptor.ATTR_ADMIN_USERNAME 保持一致；为避免对 fitcoach-admin 的强依赖，这里复刻常量字面量 */
    private static final String ATTR_ADMIN_USERNAME = "admin.username";

    private final LogPullService logPullService;
    private final LogStorageService storageService;
    /** 审计端口，required=false：在没有 fitcoach-admin 模块时可缺省（如纯客户端日志服务部署模式） */
    private final AdminAuditPort auditPort;

    public AdminLogController(LogPullService logPullService,
                              LogStorageService storageService,
                              @Autowired(required = false) AdminAuditPort auditPort) {
        this.logPullService = logPullService;
        this.storageService = storageService;
        this.auditPort = auditPort;
    }

    /**
     * 创建任务
     * <p>POST /api/admin/logs/tasks  (application/json)
     */
    @PostMapping
    public Result<LogTaskDto> create(HttpServletRequest request,
                                     @RequestBody(required = false) CreateLogTaskRequest body) {
        String operator = (String) request.getAttribute(ATTR_ADMIN_USERNAME);
        String targetUid = body != null && body.getUid() != null ? body.getUid().trim() : "(null)";
        try {
            LogTaskDto dto = logPullService.createTask(body, operator);
            if (auditPort != null) {
                auditPort.logSuccess(request, "CREATE_LOG_TASK",
                        "LOG_TASK", String.valueOf(dto.getId()),
                        String.format("targetUid=%s, recentHours=%s",
                                targetUid, body != null ? body.getRecentHours() : null));
            }
            return Result.success(dto);
        } catch (RuntimeException e) {
            if (auditPort != null) {
                auditPort.logFailure(request, "CREATE_LOG_TASK",
                        "LOG_TASK", null,
                        String.format("targetUid=%s", targetUid), e.getMessage());
            }
            throw e;
        }
    }

    /**
     * 按 uid 分页查询任务
     * <p>GET /api/admin/logs/tasks?uid=xxx&status=PENDING&page=1&size=20
     */
    @GetMapping
    public Result<PageVO<LogTaskDto>> list(@RequestParam("uid") String uid,
                                           @RequestParam(value = "status", required = false) String status,
                                           @RequestParam(value = "page", defaultValue = "1") int page,
                                           @RequestParam(value = "size", defaultValue = "20") int size,
                                           HttpServletRequest request) {
        Page<LogPullTask> p = logPullService.listByUid(uid, status, page, size);
        String urlPrefix = downloadUrlBase(request);
        List<LogTaskDto> records = p.getContent().stream()
                .map(t -> LogTaskDto.from(t, buildDownloadUrl(urlPrefix, t)))
                .toList();
        return Result.success(new PageVO<>(p.getNumber() + 1, p.getSize(), p.getTotalElements(), records));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<LogTaskDto> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        LogPullTask task = logPullService.getTaskOrThrow(id);
        String urlPrefix = downloadUrlBase(request);
        return Result.success(LogTaskDto.from(task, buildDownloadUrl(urlPrefix, task)));
    }

    /**
     * 流式下载 zip。
     * <p>GET /api/admin/logs/tasks/{id}/download
     * <p>用 FileSystemResource —— Spring 会自动走 zero-copy 流式输出，不会一次性把 50MB 加载到内存。
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable("id") Long id) {
        LogPullTask task = logPullService.getTaskOrThrow(id);
        if (task.getStatus() != LogPullStatus.UPLOADED || task.getFileRelativePath() == null) {
            throw new BusinessException(ResultCode.LOG_TASK_NOT_DOWNLOADABLE);
        }
        Path path = storageService.resolveAbsolute(task.getFileRelativePath());
        if (!Files.exists(path)) {
            log.warn("日志文件不存在或已被清理, taskId={}, path={}", id, path);
            throw new BusinessException(ResultCode.LOG_TASK_FILE_MISSING);
        }
        long contentLength;
        try {
            contentLength = Files.size(path);
        } catch (IOException e) {
            log.error("读取日志文件大小失败, taskId={}, path={}", id, path, e);
            throw new BusinessException(ResultCode.LOG_DOWNLOAD_IO_ERROR);
        }
        // 文件名 — 服务端提供建议名：fitcoach_log_<uid>_<taskId>.zip
        String suggestedName = "fitcoach_log_" + task.getUid() + "_" + task.getId() + ".zip";
        // RFC 5987 编码以兼容含中文 uid 的极端场景（虽然当前 uid 是字母数字）
        String encoded = URLEncoder.encode(suggestedName, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", suggestedName);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + suggestedName + "\"; filename*=UTF-8''" + encoded);
        log.info("admin 下载日志, taskId={}, uid={}, sizeB={}", id, task.getUid(), contentLength);
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(contentLength)
                .body(new FileSystemResource(path));
    }

    /** 删除任务（同步删盘） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id, HttpServletRequest request) {
        String operator = (String) request.getAttribute(ATTR_ADMIN_USERNAME);
        try {
            logPullService.deleteTask(id, operator);
            if (auditPort != null) {
                auditPort.logSuccess(request, "DELETE_LOG_TASK",
                        "LOG_TASK", String.valueOf(id), "delete + remove file");
            }
            return Result.success(null);
        } catch (RuntimeException e) {
            if (auditPort != null) {
                auditPort.logFailure(request, "DELETE_LOG_TASK",
                        "LOG_TASK", String.valueOf(id), "delete + remove file", e.getMessage());
            }
            throw e;
        }
    }

    // ====== 内部 ======

    private String downloadUrlBase(HttpServletRequest request) {
        // 用 contextPath 拼相对 URL，前端会自己拼 baseURL；不读 request 的 host，避免反代场景下 host 错误
        String ctx = request.getContextPath() == null ? "" : request.getContextPath();
        return ctx + "/api/admin/logs/tasks";
    }

    private String buildDownloadUrl(String prefix, LogPullTask task) {
        if (task.getStatus() != LogPullStatus.UPLOADED) return null;
        return prefix + "/" + task.getId() + "/download";
    }

    /**
     * 私有分页 VO，避免引入 fitcoach-admin 的 PageResponse（fitcoach-log 不应反向依赖 admin）。
     * 字段与 admin 的 PageResponse 完全一致，前端无差异。
     */
    public record PageVO<T>(int page, int size, long total, List<T> records) {}
}
