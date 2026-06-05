package com.lanprojects.fitcoach.appversion.service;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * App 版本文件（安装包 / Mapping）存储服务。
 *
 * <p>目录结构：{@code <baseDir>/<subDir>/<platform>/<versionCode>/<yyyyMM>/<type>_<uuid>.<ext>}
 * <ul>
 *   <li>按平台+版本号+月份分层，清晰且便于批量清理旧版本文件；</li>
 *   <li>type 前缀（package / mapping）+ UUID，防文件名冲突。</li>
 * </ul>
 *
 * <p>参考 {@link com.lanprojects.fitcoach.login.service.LocalAvatarStorageService} 实现模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppVersionFileStorageService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final UploadProperties uploadProperties;

    /**
     * 存储安装包文件（APK / IPA）。
     *
     * @param platform    平台：android / ios
     * @param versionCode 版本号
     * @param file        上传文件
     * @return 文件元数据结果
     */
    public FileStoreResult savePackageFile(String platform, Integer versionCode, MultipartFile file) {
        UploadProperties.AppVersion cfg = uploadProperties.getAppversion();

        // 校验
        validateNotEmpty(file);
        validateSize(file, cfg.getPackageMaxSizeBytes());
        // APK/IPA 都是 ZIP 格式
        if (!FileMagicValidator.isZipFormat(file)) {
            throw new BusinessException(ResultCode.APP_VERSION_FILE_TYPE_INVALID,
                    "安装包必须是有效的 APK/IPA 文件（ZIP 格式）");
        }

        String ext = resolvePackageExtension(platform);
        return storeFile(cfg.getSubDir(), platform, versionCode, "package", ext, file);
    }

    /**
     * 存储 Mapping 文件（ProGuard/R8 mapping.txt）。
     *
     * @param platform    平台（仅 android）
     * @param versionCode 版本号
     * @param file        上传文件（.txt / .map）
     * @return 文件元数据结果
     */
    public FileStoreResult saveMappingFile(String platform, Integer versionCode, MultipartFile file) {
        UploadProperties.AppVersion cfg = uploadProperties.getAppversion();

        validateNotEmpty(file);
        validateSize(file, cfg.getMappingMaxSizeBytes());
        // Mapping 文件是纯文本，不做 magic number 校验（无标准 magic）

        return storeFile(cfg.getSubDir(), platform, versionCode, "mapping", "txt", file);
    }

    /**
     * 删除磁盘上的文件（安静模式：文件不存在不报错）。
     *
     * @param fileUrl 文件 URL（如 /static/appversion/android/1002003/202506/package_xxx.apk）
     */
    public void deleteFileByUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        // 从 URL 反推相对路径：去掉 urlPrefix
        String prefix = trimTrailingSlash(uploadProperties.getUrlPrefix());
        String relativePath;
        if (fileUrl.startsWith(prefix + "/")) {
            relativePath = fileUrl.substring(prefix.length() + 1);
        } else {
            // URL 格式不匹配，无法定位文件
            log.warn("[appversion-file] 无法从 URL 反推文件路径, url={}", fileUrl);
            return;
        }

        Path baseDir = Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize();
        Path target = baseDir.resolve(relativePath).normalize();
        if (!target.startsWith(baseDir)) {
            log.warn("[appversion-file] 路径越界, relativePath={}, resolved={}", relativePath, target);
            return;
        }

        try {
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) {
                log.info("[appversion-file] 已删除文件: {}", target);
            }
        } catch (IOException e) {
            log.warn("[appversion-file] 删除文件失败: {}", target, e);
        }
    }

    // ====== 内部方法 ======

    private FileStoreResult storeFile(String subDir, String platform, Integer versionCode,
                                       String typePrefix, String ext, MultipartFile file) {
        String month = LocalDate.now().format(MONTH_FMT);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String filename = typePrefix + "_" + uuid + "." + ext;

        // 相对路径：appversion/android/1002003/202506/package_xxx.apk
        String relativePath = subDir + "/" + platform + "/" + versionCode + "/" + month + "/" + filename;
        Path baseDir = Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize();
        Path target = baseDir.resolve(relativePath).normalize();

        // 路径越界保护
        if (!target.startsWith(baseDir)) {
            log.warn("[appversion-file] 目标路径越界, platform={}, versionCode={}, target={}, baseDir={}",
                    platform, versionCode, target, baseDir);
            throw new BusinessException(ResultCode.APP_VERSION_FILE_STORAGE_ERROR);
        }

        // 写盘 + 同步计算 MD5
        String md5;
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream in = file.getInputStream()) {
                // 流式写盘 + MD5 计算（不将整个文件加载到内存）
                byte[] buffer = new byte[8192];
                int bytesRead;
                var out = Files.newOutputStream(target);
                try {
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        digest.update(buffer, 0, bytesRead);
                    }
                } finally {
                    out.close();
                }
            }
            md5 = bytesToHex(digest.digest());
        } catch (IOException e) {
            log.error("[appversion-file] 文件写盘失败, platform={}, versionCode={}, target={}",
                    platform, versionCode, target, e);
            throw new BusinessException(ResultCode.APP_VERSION_FILE_STORAGE_ERROR);
        } catch (NoSuchAlgorithmException e) {
            // MD5 是 JDK 内置算法，理论上不会走到这里
            throw new RuntimeException("MD5 algorithm not available", e);
        }

        String prefix = trimTrailingSlash(uploadProperties.getUrlPrefix());
        String url = prefix + "/" + relativePath;

        log.info("[appversion-file] 文件保存成功, type={}, platform={}, versionCode={}, size={}B, md5={}, url={}",
                typePrefix, platform, versionCode, file.getSize(), md5, url);

        return new FileStoreResult(
                url,
                file.getSize(),
                md5,
                file.getOriginalFilename()
        );
    }

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.APP_VERSION_FILE_EMPTY);
        }
    }

    private void validateSize(MultipartFile file, long maxBytes) {
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ResultCode.APP_VERSION_FILE_TOO_LARGE,
                    String.format("文件过大（%dMB），最大允许 %dMB",
                            file.getSize() / (1024 * 1024), maxBytes / (1024 * 1024)));
        }
    }

    private String resolvePackageExtension(String platform) {
        return "android".equals(platform) ? "apk" : "ipa";
    }

    private String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 文件存储结果（不可变记录）。
     *
     * @param url      完整访问 URL（如 /static/appversion/android/1002003/202506/package_xxx.apk）
     * @param size     文件大小（字节）
     * @param md5      MD5 校验和（32 字符小写 hex）
     * @param fileName 原始文件名
     */
    public record FileStoreResult(String url, long size, String md5, String fileName) {}
}
