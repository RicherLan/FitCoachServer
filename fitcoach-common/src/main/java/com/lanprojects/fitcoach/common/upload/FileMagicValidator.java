package com.lanprojects.fitcoach.common.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 上传文件 magic number 校验器 — 防止客户端伪造 Content-Type 把非图片文件（如 exe / sh）
 * 当作图片上传到服务端的兜底防线。
 *
 * <p><b>背景</b>：HTTP 上传里的 Content-Type Header 完全由客户端控制，攻击者可以
 * 把 .apk / .exe 改 Header 为 image/jpeg 提交。仅依赖白名单校验 contentType 不安全 —
 * 需要读文件头几个字节比对真实格式。
 *
 * <p><b>支持的格式</b>：
 * <ul>
 *   <li>JPEG: {@code FF D8 FF}</li>
 *   <li>PNG:  {@code 89 50 4E 47 0D 0A 1A 0A}</li>
 *   <li>WebP: {@code 52 49 46 46 ?? ?? ?? ?? 57 45 42 50}（RIFF????WEBP）</li>
 *   <li>GIF:  {@code 47 49 46 38 39 61} / {@code 47 49 46 38 37 61}</li>
 *   <li>ZIP/APK/IPA: {@code 50 4B 03 04}（PK\x03\x04）</li>
 * </ul>
 *
 * <p><b>使用方式</b>：业务层先做 Content-Type 白名单 + size 校验，再调本工具二次校验真实格式：
 * <pre>
 *   if (!FileMagicValidator.matchesContentType(file, contentType)) {
 *       throw new BusinessException(...);
 *   }
 * </pre>
 *
 * <p><b>性能</b>：仅读前 12 字节，对小图无感；MultipartFile 在 Spring 实现下可重复打开输入流。
 */
@Slf4j
public final class FileMagicValidator {

    private FileMagicValidator() {}

    /** 仅读这么多字节做 magic number 判定，足够覆盖目前所有支持格式 */
    private static final int HEADER_SIZE = 12;

    /**
     * 检查上传文件的真实 magic number 是否与声明的 contentType 匹配。
     *
     * @param file        上传文件（要求 {@link MultipartFile#getInputStream()} 可重复读取，
     *                    Spring StandardMultipartFile / CommonsMultipartFile 都满足）
     * @param contentType 声明的 MIME type（来自 {@link MultipartFile#getContentType()}），
     *                    nullable — null 时直接返回 false
     * @return true 表示真实文件头与声明 MIME 一致；false 表示文件损坏 / 类型不符 / 读取失败
     */
    public static boolean matchesContentType(MultipartFile file, String contentType) {
        if (file == null || contentType == null) {
            return false;
        }
        byte[] header = readHeader(file);
        if (header == null) {
            return false;
        }
        String type = contentType.toLowerCase().trim();
        return switch (type) {
            case "image/jpeg", "image/jpg" -> isJpeg(header);
            case "image/png" -> isPng(header);
            case "image/webp" -> isWebp(header);
            case "image/gif" -> isGif(header);
            case "application/zip",
                 "application/vnd.android.package-archive",   // APK
                 "application/x-ios-app",                     // IPA (显式声明时)
                 "application/octet-stream" -> isZip(header); // APK/IPA 通用兜底
            default -> {
                log.warn("[file-magic] 暂不支持的 contentType={}，跳过 magic 校验", type);
                yield false;
            }
        };
    }

    /** 仅判定文件是否为已知图片格式（与 contentType 无关），便于二次防御使用 */
    public static boolean isKnownImage(MultipartFile file) {
        byte[] header = readHeader(file);
        if (header == null) return false;
        return isJpeg(header) || isPng(header) || isWebp(header) || isGif(header);
    }

    /** 仅判定文件是否为 ZIP 格式（APK/IPA 都是 ZIP），便于安装包校验 */
    public static boolean isZipFormat(MultipartFile file) {
        byte[] header = readHeader(file);
        if (header == null) return false;
        return isZip(header);
    }

    // ====== 各格式头识别 ======

    private static boolean isJpeg(byte[] h) {
        return h.length >= 3
                && (h[0] & 0xFF) == 0xFF
                && (h[1] & 0xFF) == 0xD8
                && (h[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] h) {
        return h.length >= 8
                && (h[0] & 0xFF) == 0x89
                && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && (h[4] & 0xFF) == 0x0D && (h[5] & 0xFF) == 0x0A
                && (h[6] & 0xFF) == 0x1A && (h[7] & 0xFF) == 0x0A;
    }

    private static boolean isWebp(byte[] h) {
        return h.length >= 12
                && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }

    private static boolean isGif(byte[] h) {
        return h.length >= 6
                && h[0] == 'G' && h[1] == 'I' && h[2] == 'F'
                && h[3] == '8' && (h[4] == '7' || h[4] == '9') && h[5] == 'a';
    }

    /** ZIP magic: {@code PK\x03\x04} — APK 和 IPA 都是标准 ZIP 归档 */
    private static boolean isZip(byte[] h) {
        return h.length >= 4
                && (h[0] & 0xFF) == 0x50  // 'P'
                && (h[1] & 0xFF) == 0x4B  // 'K'
                && (h[2] & 0xFF) == 0x03
                && (h[3] & 0xFF) == 0x04;
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] buf = new byte[HEADER_SIZE];
            int total = 0;
            while (total < HEADER_SIZE) {
                int read = in.read(buf, total, HEADER_SIZE - total);
                if (read < 0) break;
                total += read;
            }
            if (total < HEADER_SIZE) {
                // 文件太小读不满 12 字节 — 直接返回部分填充的数组，调用方比对会失败
                byte[] partial = new byte[total];
                System.arraycopy(buf, 0, partial, 0, total);
                return partial;
            }
            return buf;
        } catch (IOException e) {
            log.warn("[file-magic] 读取文件头失败 name={} size={}",
                    file.getOriginalFilename(), file.getSize(), e);
            return null;
        }
    }
}
