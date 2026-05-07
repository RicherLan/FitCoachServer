package com.lanprojects.fitcoach.log.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.upload.UploadProperties;
import com.lanprojects.fitcoach.log.config.LogProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 日志 zip 文件的本地磁盘存储服务。
 *
 * <p>目录结构：{@code <upload.base-dir>/<log-pull.sub-dir>/<uid>/<taskId>_<timestamp>.zip}
 *
 * <ul>
 *   <li>按 uid 分目录，方便 admin 直接 SSH 上去按用户排查；</li>
 *   <li>文件名用 {@code <taskId>_<上传时间戳ms>.zip}：taskId 关联回任务，时间戳防止幂等场景的同名覆盖（虽然
 *       业务上 UPLOADED 后不会再写，但保留时间戳便于后续如果开放 "重传" 也兼容）；</li>
 *   <li>resolve 后做 startsWith(baseDir) 越界保护，参考 LocalAvatarStorageService / LocalFeedbackAttachmentStorageService。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogStorageService {

    private final UploadProperties uploadProperties;
    private final LogProperties logProperties;

    /**
     * 把客户端上传的 zip 写到磁盘。
     *
     * @return 相对路径（{@code log-pull.sub-dir/<uid>/<filename>}），用于落库 fileRelativePath
     */
    public String saveLogZip(String uid, Long taskId, MultipartFile file) {
        String filename = taskId + "_" + System.currentTimeMillis() + ".zip";
        String relativePath = logProperties.getSubDir() + "/" + uid + "/" + filename;

        Path baseDir = Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize();
        Path target = baseDir.resolve(logProperties.getSubDir())
                .resolve(uid)
                .resolve(filename)
                .normalize();

        // 越界保护 — uid 异常带 ../ 时仍保证落盘点在 baseDir 下
        if (!target.startsWith(baseDir)) {
            log.warn("日志文件目标路径越界, uid={}, taskId={}, target={}, baseDir={}",
                    uid, taskId, target, baseDir);
            throw new BusinessException(ResultCode.LOG_UPLOAD_STORAGE_ERROR);
        }

        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("日志文件写盘失败, uid={}, taskId={}, target={}", uid, taskId, target, e);
            throw new BusinessException(ResultCode.LOG_UPLOAD_STORAGE_ERROR);
        }

        log.info("日志文件保存成功, uid={}, taskId={}, size={}B, relativePath={}",
                uid, taskId, file.getSize(), relativePath);
        return relativePath;
    }

    /**
     * 把相对路径解析回绝对 Path。
     * <p>越界保护同 saveLogZip。
     */
    public Path resolveAbsolute(String relativePath) {
        Path baseDir = Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize();
        Path target = baseDir.resolve(relativePath).normalize();
        if (!target.startsWith(baseDir)) {
            log.warn("日志文件解析路径越界, relativePath={}, baseDir={}", relativePath, baseDir);
            throw new BusinessException(ResultCode.LOG_DOWNLOAD_IO_ERROR);
        }
        return target;
    }

    /**
     * 删除指定相对路径的日志文件。文件不存在视为成功（幂等）。
     */
    public void deleteIfExists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Path absolute = resolveAbsolute(relativePath);
            boolean removed = Files.deleteIfExists(absolute);
            if (removed) {
                log.info("日志文件已删除, relativePath={}", relativePath);
            }
        } catch (IOException e) {
            // 删除失败不抛业务异常 — 调用方通常是 scheduler / admin delete，
            // 落盘已经是历史，不能因清理失败影响主流程
            log.warn("日志文件删除失败（可忽略，下次扫描重试）, relativePath={}", relativePath, e);
        }
    }
}
