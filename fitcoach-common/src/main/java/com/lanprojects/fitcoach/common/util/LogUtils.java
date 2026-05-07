package com.lanprojects.fitcoach.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 日志脱敏工具
 * <p>
 * 用于在日志中代替明文敏感信息（openId / unionId / 微信授权码 / token 等），
 * 既能用 hash 关联同一条记录的多次出现，又不会泄露原始值。
 */
public final class LogUtils {

    private LogUtils() {
    }

    /**
     * 把敏感字符串转成 hash 短串用于日志输出。
     * <ul>
     *   <li>null / 空 → "null"</li>
     *   <li>非空 → "{prefix3}…{sha256前12位}"，例如 "abc…1f8d2c3a4b5e"</li>
     * </ul>
     * 既保留少量可读性辅助排查，又确保完整值不被还原。
     */
    public static String mask(String raw) {
        if (raw == null) {
            return "null";
        }
        if (raw.isEmpty()) {
            return "<empty>";
        }
        String prefix = raw.length() <= 3 ? raw : raw.substring(0, 3);
        return prefix + "…" + sha256Short(raw);
    }

    /**
     * 取 sha256 前 12 个 hex 字符（48 bit），日志可读、不可逆。
     */
    public static String sha256Short(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", digest[i] & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 内置算法，正常不会发生
            return "nohash";
        }
    }
}
