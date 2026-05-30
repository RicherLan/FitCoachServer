package com.lanprojects.fitcoach.login.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.util.LogUtils;
import com.lanprojects.fitcoach.login.sms.SmsException;
import com.lanprojects.fitcoach.login.sms.SmsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OTP 验证码服务 — 生成 / 存储 / 校验 / 频率限制
 * <p>
 * 设计要点：
 * <ul>
 *   <li>OTP 用 {@link SecureRandom} 生成 6 位数字；</li>
 *   <li>存储用 Caffeine 本地缓存（TTL = ttlSeconds），到期自动失效；</li>
 *   <li>同一手机号 60s 内不能重发；1 小时内最多发 N 次；</li>
 *   <li>校验失败累计 N 次 → 该 OTP 立刻作废，必须重新发送；</li>
 *   <li>校验成功立刻删除 OTP，防止重放；</li>
 *   <li>handle 异常一律不暴露内部细节，只对外抛 {@link ResultCode} 业务码。</li>
 * </ul>
 *
 * <p><b>多实例部署提醒</b>：当前用 Caffeine 本地缓存，单实例足够；多实例部署后必须切到 Redis（替换本类内部即可，
 * 接口不变，业务调用方零感知）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RNG = new SecureRandom();

    @Value("${otp.length:6}")
    private int otpLength;

    @Value("${otp.ttl-seconds:300}")
    private int ttlSeconds;

    @Value("${otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    @Value("${otp.max-per-hour-per-phone:5}")
    private int maxPerHourPerPhone;

    @Value("${otp.max-per-hour-per-ip:20}")
    private int maxPerHourPerIp;

    @Value("${otp.max-verify-attempts:5}")
    private int maxVerifyAttempts;

    private final SmsService smsService;

    // ====== 缓存定义（PostConstruct 中按配置初始化） ======

    /** key=phone, value=OtpEntry */
    private Cache<String, OtpEntry> otpStore;

    /** key=phone，记录"最近一次发送时刻 + 1h 内累计次数" */
    private Cache<String, SendQuota> phoneQuota;

    /** key=ip，记录 1h 内累计次数 */
    private Cache<String, AtomicInteger> ipQuota;

    @PostConstruct
    public void init() {
        this.otpStore = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(100_000)
                .build();
        this.phoneQuota = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(100_000)
                .build();
        this.ipQuota = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(50_000)
                .build();
        log.info("OtpService 初始化完成: length={}, ttl={}s, cooldown={}s, perHourPerPhone={}, perHourPerIp={}, maxVerifyAttempts={}",
                otpLength, ttlSeconds, resendCooldownSeconds, maxPerHourPerPhone, maxPerHourPerIp, maxVerifyAttempts);
    }

    // ====== 对外 API ======

    /**
     * 申请发送验证码。失败抛 {@link BusinessException}（带具体 ResultCode）。
     *
     * @param phone     11 位国内手机号（控制器层已 @Valid 校验过）
     * @param clientIp  请求 IP（用于 IP 维度限频；可传 null，传 null 时只走手机维度）
     */
    public void requestOtp(String phone, String clientIp) {
        long now = System.currentTimeMillis();

        // 1) 手机号级别 - 重发冷却
        SendQuota quota = phoneQuota.get(phone, k -> new SendQuota());
        synchronized (quota) {
            long sinceLastMs = now - quota.lastSendAt;
            if (quota.lastSendAt > 0 && sinceLastMs < resendCooldownSeconds * 1000L) {
                throw new BusinessException(ResultCode.OTP_SEND_TOO_FAST);
            }
            // 2) 手机号级别 - 1h 计数
            if (quota.countLastHour >= maxPerHourPerPhone) {
                throw new BusinessException(ResultCode.OTP_SEND_LIMIT_EXCEEDED);
            }
        }

        // 3) IP 级别限频
        if (clientIp != null && !clientIp.isBlank()) {
            AtomicInteger ipCount = ipQuota.get(clientIp, k -> new AtomicInteger(0));
            if (ipCount.incrementAndGet() > maxPerHourPerIp) {
                log.warn("IP {} 1 小时内 SMS 请求超限", LogUtils.mask(clientIp));
                throw new BusinessException(ResultCode.OTP_SEND_LIMIT_EXCEEDED);
            }
        }

        // 4) 生成 OTP & 入缓存（每次发送都覆盖旧 OTP，旧 OTP 立即失效）
        String code = generateOtp();
        otpStore.put(phone, new OtpEntry(code, new AtomicInteger(0)));

        // 5) 调短信网关 —— 失败时回滚冷却计数（防止用户因下游故障被锁）
        try {
            smsService.sendOtp(phone, code, ttlSeconds / 60);
        } catch (SmsException e) {
            otpStore.invalidate(phone);
            log.error("SMS 下发失败, phone={}, err={}", LogUtils.mask(phone), e.getMessage());
            throw new BusinessException(ResultCode.SMS_PROVIDER_ERROR);
        }

        // 6) 标记成功 —— 写入冷却时间和计数
        synchronized (quota) {
            quota.lastSendAt = now;
            quota.countLastHour++;
        }
        log.info("OTP 已发送, phone={}, ttl={}s", LogUtils.mask(phone), ttlSeconds);
    }

    /**
     * 校验验证码。
     * <ul>
     *   <li>正确 → 立刻删除 OTP（防重放），返回</li>
     *   <li>错误 → 累计失败次数；超过 maxVerifyAttempts 立刻作废 OTP</li>
     * </ul>
     */
    public void verifyOtp(String phone, String code) {
        // 防御性：Caffeine 不接受 null key；上游 controller 已 @Valid 但内部调用也可能漏校验
        if (phone == null || phone.isBlank() || code == null) {
            throw new BusinessException(ResultCode.OTP_INVALID);
        }
        OtpEntry entry = otpStore.getIfPresent(phone);
        if (entry == null) {
            throw new BusinessException(ResultCode.OTP_INVALID);
        }
        // 错误次数已超 → 直接作废 OTP，要求重发
        if (entry.attempts.get() >= maxVerifyAttempts) {
            otpStore.invalidate(phone);
            throw new BusinessException(ResultCode.OTP_VERIFY_LIMIT_EXCEEDED);
        }
        // 用 equals 而不是 ==（字符串内容比较）
        if (!entry.code.equals(code.trim())) {
            int after = entry.attempts.incrementAndGet();
            log.info("OTP 校验失败, phone={}, attempts={}/{}", LogUtils.mask(phone), after, maxVerifyAttempts);
            if (after >= maxVerifyAttempts) {
                otpStore.invalidate(phone);
            }
            throw new BusinessException(ResultCode.OTP_INVALID);
        }
        // 校验通过 → 立刻删 key（防重放）
        otpStore.invalidate(phone);
        log.info("OTP 校验成功, phone={}", LogUtils.mask(phone));
    }

    // ====== 内部 ======

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(RNG.nextInt(10));
        }
        return sb.toString();
    }

    /** 单条 OTP 记录 */
    private record OtpEntry(String code, AtomicInteger attempts) {}

    /** 单手机号的发送配额（重发冷却 + 1h 计数）；put 进 phoneQuota 后被 1h 自动驱逐 */
    private static class SendQuota {
        long lastSendAt = 0L;
        int countLastHour = 0;
    }
}
