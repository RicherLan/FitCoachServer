package com.lanprojects.fitcoach.common.config.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 系统配置加解密服务（AES-GCM）
 * <p>
 * <b>master key 来源（按优先级）：</b>
 * <ol>
 *   <li>环境变量 <code>FITCOACH_MASTER_KEY</code>（生产推荐）</li>
 *   <li>JVM 启动参数 <code>-Dfitcoach.master.key=...</code></li>
 *   <li>本地文件 <code>${user.home}/.fitcoach/master.key</code>（开发兜底，首次自动生成）</li>
 * </ol>
 * <p>
 * <b>密文格式：</b>
 * <pre>"v1:" + base64( IV(12B) || cipherText )</pre>
 * 版本前缀方便未来无缝升级算法。
 */
@Slf4j
@Service
public class ConfigCryptoService {

    public static final String CIPHER_PREFIX = "v1:";
    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec keySpec;

    @Value("${fitcoach.master.key:}")
    private String injectedMasterKey;

    @PostConstruct
    public void init() {
        String masterKey = resolveMasterKey();
        // 用 SHA-256 把任意长度的 master key 派生成 32 字节 AES-256 密钥
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            this.keySpec = new SecretKeySpec(keyBytes, AES);
            log.info("ConfigCryptoService 初始化完成（AES-256-GCM）");
        } catch (Exception e) {
            throw new IllegalStateException("ConfigCryptoService 初始化失败", e);
        }
    }

    /**
     * 加密明文 → 带版本前缀的 base64 密文。
     * 入参为 null 或空时原样返回，便于初始化空配置。
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return CIPHER_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("配置加密失败", e);
        }
    }

    /**
     * 解密带版本前缀的密文。空值原样返回。
     * 不带 v1: 前缀的会被认为是历史明文（向前兼容旧配置）。
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        if (!ciphertext.startsWith(CIPHER_PREFIX)) {
            // 兼容历史明文配置：直接返回。但生产应避免这种情况。
            log.warn("配置值未带加密前缀，按明文兜底返回；建议尽快迁移为加密存储");
            return ciphertext;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring(CIPHER_PREFIX.length()));
            if (combined.length <= GCM_IV_BYTES) {
                throw new IllegalArgumentException("密文长度不合法");
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] cipherText = new byte[combined.length - GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(combined, GCM_IV_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("配置解密失败：master key 是否更换或密文损坏？", e);
        }
    }

    // ====== master key 解析 ======

    private String resolveMasterKey() {
        // 1. 环境变量
        String envKey = System.getenv("FITCOACH_MASTER_KEY");
        if (envKey != null && !envKey.isBlank()) {
            log.info("master key 来源：环境变量 FITCOACH_MASTER_KEY");
            return envKey;
        }
        // 2. JVM 系统属性 / Spring 注入
        if (injectedMasterKey != null && !injectedMasterKey.isBlank()) {
            log.info("master key 来源：JVM 启动参数 fitcoach.master.key");
            return injectedMasterKey;
        }
        // 3. 本地文件兜底（开发用）
        Path keyFile = Path.of(System.getProperty("user.home"), ".fitcoach", "master.key");
        try {
            if (Files.exists(keyFile)) {
                String key = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
                if (!key.isBlank()) {
                    log.warn("master key 来源：本地文件 {}（仅限开发；生产请使用环境变量）", keyFile);
                    return key;
                }
            }
            // 首次启动自动生成
            Files.createDirectories(keyFile.getParent());
            String generated = generateRandomKey();
            Files.writeString(keyFile, generated + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            // 尝试设置只读权限（POSIX 系统）
            try {
                keyFile.toFile().setReadable(false, false);
                keyFile.toFile().setReadable(true, true);
                keyFile.toFile().setWritable(false, false);
                keyFile.toFile().setWritable(true, true);
            } catch (Exception ignore) {
                // Windows 等系统不支持，忽略即可
            }
            log.warn("master key 已自动生成并写入 {}（仅限开发；生产请使用环境变量）", keyFile);
            return generated;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "无法读取或创建 master key 文件 " + keyFile + "；请通过环境变量 FITCOACH_MASTER_KEY 注入", e);
        }
    }

    private String generateRandomKey() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        return Base64.getEncoder().encodeToString(random);
    }
}
