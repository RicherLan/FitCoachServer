package com.lanprojects.fitcoach.trainingrecord.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.upload.FileMagicValidator;
import com.lanprojects.fitcoach.common.upload.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 训练动作自定义图标存储服务。
 *
 * <p>目录结构：{@code <baseDir>/<subDir>/<exerciseKey>/<uuid>.<ext>}
 * <ul>
 *   <li>按 exerciseKey 一层目录便于运维肉眼定位某个动作的图标文件；</li>
 *   <li>文件名带 UUID 避免覆盖竞态（业务层会先 delete 旧文件再写新文件，但 UUID
 *       也保证两次连续上传不会因为同名而互相覆盖）。</li>
 * </ul>
 *
 * <p>实现模式完全参考 {@code AppVersionFileStorageService}：
 * Files.copy 流式写盘 + Content-Type / magic number 双重校验 + 越界保护 + delete 静默幂等。
 *
 * <p><b>线程安全</b>：依赖文件系统原子写入（操作系统层），不做应用级锁；同一 exerciseKey
 * 并发两次上传最终只会保留后写入者，符合"覆盖"语义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingExerciseIconStorageService {

    private final UploadProperties uploadProperties;

    /**
     * 存储图标文件，返回相对 URL 与文件元数据。
     *
     * @param exerciseKey 动作业务 key，用作子目录名（如 BARBELL_BENCH_PRESS）
     * @param file        上传文件（JPEG/PNG/WebP）
     * @return 文件 URL / 大小 / 原始文件名
     */
    public IconStoreResult saveIcon(String exerciseKey, MultipartFile file) {
        UploadProperties.TrainingExerciseIcon cfg = uploadProperties.getTrainingExerciseIcon();

        // 1. 基础校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.TRAINING_EXERCISE_ICON_EMPTY);
        }
        if (file.getSize() > cfg.getMaxSizeBytes()) {
            throw new BusinessException(ResultCode.TRAINING_EXERCISE_ICON_TOO_LARGE,
                    String.format("图标过大（%dKB），最大允许 %dKB",
                            file.getSize() / 1024, cfg.getMaxSizeBytes() / 1024));
        }
        // 2. Content-Type 白名单（客户端声明）
        String contentType = file.getContentType();
        if (contentType == null || !cfg.getAllowedContentTypesView().contains(contentType.toLowerCase())) {
            throw new BusinessException(ResultCode.TRAINING_EXERCISE_ICON_TYPE_INVALID,
                    "Content-Type 不在白名单：" + contentType);
        }
        // 3. magic number 二次校验（防伪 Content-Type 把脚本当图片传上来）
        if (!FileMagicValidator.matchesContentType(file, contentType)) {
            throw new BusinessException(ResultCode.TRAINING_EXERCISE_ICON_TYPE_INVALID,
                    "文件真实格式与 Content-Type 不符");
        }

        // 4. 算路径：<baseDir>/<subDir>/<exerciseKey>/<uuid>.<ext>
        String ext = resolveExtension(contentType);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String filename = uuid + "." + ext;
        String relativePath = cfg.getSubDir() + "/" + exerciseKey + "/" + filename;

        Path baseDir = Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize();
        Path target = baseDir.resolve(relativePath).normalize();

        // 5. 越界保护（exerciseKey 由 Service 层走正则校验过，理论不会出此问题，但兜底）
        if (!target.startsWith(baseDir)) {
            log.warn("[trainingexercise-icon] 目标路径越界, exerciseKey={}, target={}, baseDir={}",
                    exerciseKey, target, baseDir);
            throw new BusinessException(ResultCode.TRAINING_EXERCISE_ICON_STORAGE_ERROR);
        }

        // 6. 写盘
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[trainingexercise-icon] 写盘失败, exerciseKey={}, target={}",
                    exerciseKey, target, e);
            throw new BusinessException(ResultCode.TRAINING_EXERCISE_ICON_STORAGE_ERROR);
        }

        String url = trimTrailingSlash(uploadProperties.getUrlPrefix()) + "/" + relativePath;
        log.info("[trainingexercise-icon] 图标保存成功, exerciseKey={}, size={}B, url={}",
                exerciseKey, file.getSize(), url);

        return new IconStoreResult(url, file.getSize(), file.getOriginalFilename());
    }

    /**
     * 删除磁盘上的旧图标（安静模式：文件不存在不报错，便于幂等调用）。
     *
     * @param fileUrl 文件 URL（如 {@code /static/trainingexercise-icon/BARBELL_BENCH_PRESS/xxx.png}）
     */
    public void deleteIconByUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        String prefix = trimTrailingSlash(uploadProperties.getUrlPrefix());
        String relativePath;
        if (fileUrl.startsWith(prefix + "/")) {
            relativePath = fileUrl.substring(prefix.length() + 1);
        } else {
            log.warn("[trainingexercise-icon] 无法从 URL 反推文件路径, url={}", fileUrl);
            return;
        }

        Path baseDir = Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize();
        Path target = baseDir.resolve(relativePath).normalize();
        if (!target.startsWith(baseDir)) {
            log.warn("[trainingexercise-icon] 路径越界, relativePath={}, resolved={}", relativePath, target);
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) {
                log.info("[trainingexercise-icon] 已删除文件: {}", target);
            }
        } catch (IOException e) {
            log.warn("[trainingexercise-icon] 删除文件失败: {}", target, e);
        }
    }

    private String resolveExtension(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "bin"; // 校验都通过了不会走这里
        };
    }

    private String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * 图标存储结果。
     *
     * @param url      相对 URL（如 {@code /static/trainingexercise-icon/.../xxx.png}）
     * @param size     文件大小（字节）
     * @param fileName 原始文件名
     */
    public record IconStoreResult(String url, long size, String fileName) {}
}
