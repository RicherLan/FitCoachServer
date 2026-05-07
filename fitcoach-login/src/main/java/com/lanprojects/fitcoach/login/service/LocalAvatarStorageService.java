package com.lanprojects.fitcoach.login.service;

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
 * 本地磁盘头像存储实现。
 * <p>
 * 目录结构：{@code <baseDir>/<avatar.subDir>/<yyyyMM>/<uid>_<uuid>.<ext>}
 * <ul>
 *   <li>按月分目录：避免单目录文件数过多导致 ls/索引性能下降；</li>
 *   <li>uid 前缀：方便人工排查 / 统计某用户的头像变更历史；</li>
 *   <li>uuid：彻底避免文件名冲突（同一用户秒级多次上传也安全）；</li>
 *   <li>扩展名优先取 contentType（jpg/png/webp），不信任客户端原文件名（防 ../ 注入）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalAvatarStorageService implements AvatarStorageService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final UploadProperties uploadProperties;

    @Override
    public String saveAvatar(String uid, MultipartFile file) {
        UploadProperties.Avatar cfg = uploadProperties.getAvatar();
        String month = LocalDate.now().format(MONTH_FMT);
        String ext = resolveExtension(file.getContentType());
        String filename = uid + "_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        // 相对路径用 / 做分隔，URL 才能正确拼接；磁盘路径走 Path 自动适配 OS 分隔符
        String relativePath = cfg.getSubDir() + "/" + month + "/" + filename;
        Path baseDir = Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize();
        Path target = baseDir.resolve(cfg.getSubDir()).resolve(month).resolve(filename).normalize();

        // 防越界 — resolve 后必须仍在 baseDir 之下，杜绝 uid 中带 ../ 等异常情况
        if (!target.startsWith(baseDir)) {
            log.warn("头像目标路径越界, uid={}, target={}, baseDir={}", uid, target, baseDir);
            throw new BusinessException(ResultCode.AVATAR_STORAGE_ERROR);
        }

        try {
            Files.createDirectories(target.getParent());
            // REPLACE_EXISTING 兜底：极小概率 UUID 碰撞或重传同名时不抛异常
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("头像写盘失败, uid={}, target={}", uid, target, e);
            throw new BusinessException(ResultCode.AVATAR_STORAGE_ERROR);
        }

        // URL 前缀去掉末尾多余的 /，相对路径必带 /，避免拼成 //
        String prefix = trimTrailingSlash(uploadProperties.getUrlPrefix());
        String url = prefix + "/" + relativePath;
        log.info("头像保存成功, uid={}, size={}B, url={}", uid, file.getSize(), url);
        return url;
    }

    /**
     * 根据 contentType 推断扩展名。
     * <p>客户端原文件名不可信（可能含 ../ 或非 ASCII），强制按 contentType 决定。
     */
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
