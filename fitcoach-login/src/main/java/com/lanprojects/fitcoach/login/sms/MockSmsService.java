package com.lanprojects.fitcoach.login.sms;

import com.lanprojects.fitcoach.common.util.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 短信实现 —— 不真发短信，只打日志（验证码可见，方便开发期联调）。
 * <p>
 * 默认启用：{@code sms.provider=mock}（或不配置）。
 * 接入腾讯云后切换为 {@link TencentSmsService}：在 application-prod.yml 设置 {@code sms.provider=tencent}。
 *
 * <p><b>安全</b>：仅本实现允许"明文打印 OTP"。生产配置切到 tencent 后，OTP 永远不会经由本类。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsService implements SmsService {

    @Override
    public void sendOtp(String phone, String code, int ttlMinutes) {
        // 手机号脱敏；OTP 仅 dev/mock 打印 —— 这里有意打明文便于开发联调
        log.info("[MOCK SMS] phone={} code={} ttlMin={}",
                LogUtils.mask(phone), code, ttlMinutes);
    }
}
