package com.lanprojects.fitcoach.feedback.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.upload.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 反馈附件本地磁盘存储实现。
 * <p>
 * 目录结构：{@code <baseDir>/<feedback.subDir>/<yyyyMM>/<uid>_<uuid>.<ext>}
 * <ul>
 *   <li>按月分目录避免单目录文件过多；</li>
 *   <li>uid 前缀便于按用户排查；</li>
 *   <li>uuid 防文件名碰撞；</li>
 *   <li>ext 走 contentType 推断，不信任客户端原文件名（防 ../ 注入）；</li>
 *   <li>resolve 后做 startsWith(baseDir) 越界保护，参考 LocalAvatarStorageService。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFeedbackAttachmentStorageService implements FeedbackAttachmentStorageService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final UploadProperties uploadProperties;

    @Override
    public String saveAttachment(String uid, MultipartFile file) {
        UploadProperties.Feedback cfg = uploadProperties.getFeedback();
        String month = LocalDate.now().format(MONTH_FMT);
        String ext = resolveExtension(file.getContentType());
        String filename = uid + "_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        // 相对路径用 / 拼接（URL 通用），磁盘路径走 Path 自动适配 OS 分隔符
        String relativePath = cfg.getSubDir() + "/" + month + "/" + filename;
        Path baseDir = Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize();
        Path target = baseDir.resolve(cfg.getSubDir()).resolve(month).resolve(filename).normalize();

        // 防越界 — uid 异常时（如带 ../）保证落盘点仍在 baseDir 之内
        if (!target.startsWith(baseDir)) {
            log.warn("反馈附件目标路径越界, uid={}, target={}, baseDir={}", uid, target, baseDir);
            throw new BusinessException(ResultCode.FEEDBACK_ATTACHMENT_STORAGE_ERROR);
        }

        try {
            Files.createDirectories(target.getParent());
            // REPLACE_EXISTING 兜底：UUID 极小概率碰撞 / 重传同名时不抛
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("反馈附件写盘失败, uid={}, target={}", uid, target, e);
            throw new BusinessException(ResultCode.FEEDBACK_ATTACHMENT_STORAGE_ERROR);
        }

        String prefix = trimTrailingSlash(uploadProperties.getUrlPrefix());
        String url = prefix + "/" + relativePath;
        log.info("反馈附件保存成功, uid={}, size={}B, url={}", uid, file.getSize(), url);
        return url;
    }

    /** 与 LocalAvatarStorageService 保持一致：仅按 contentType 推断扩展名 */
    private String resolveExtension(String contentType) {
        if (contentType == null) {
            return "jpg";
        }
        String lower = contentType.toLowerCase(Locale.ROOT).trim();
        return switch (lower) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
