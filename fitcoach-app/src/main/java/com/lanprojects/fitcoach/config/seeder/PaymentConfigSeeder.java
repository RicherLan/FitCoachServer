package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import com.lanprojects.fitcoach.common.config.repository.SysConfigRepository;
import com.lanprojects.fitcoach.common.config.service.ConfigCryptoService;
import com.lanprojects.fitcoach.payment.provider.PaymentConfigKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 支付通道相关 SysConfig 默认值播种。
 *
 * <p>开发期默认 {@code payment.mock.enabled=true}，下单立即返回成功，无需配置任何外部商户号。
 * <p><b>生产 profile 下，首次插入时 {@code payment.mock.enabled} 默认 false</b>，
 * 避免被 PaymentProductionGuard 拒启动。
 *
 * <p>生产部署前，运维必须：
 * <ol>
 *   <li>填入 wechat 商户号 / api key / notifyUrl；</li>
 *   <li>苹果开发者账号到位后填入 apple bundle id 和 shared secret 并 enable。</li>
 * </ol>
 */
@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
public class PaymentConfigSeeder implements CommandLineRunner {

    /** 视为"生产"的 spring profile 名字（小写比较）。"prod" / "production" 都算。 */
    private static final List<String> PROD_PROFILE_NAMES = Arrays.asList("prod", "production");

    private final SysConfigRepository sysConfigRepository;
    private final ConfigCryptoService configCryptoService;
    private final Environment environment;

    @Override
    public void run(String... args) {
        int inserted = 0;

        // 全局开关 — 开发期 Mock 启用，生产首次默认 false（与 PaymentProductionGuard 对齐）
        boolean isProd = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(PROD_PROFILE_NAMES::contains);
        String mockDefault = isProd ? "false" : "true";
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.MOCK_ENABLED, mockDefault, "payment",
                "Mock 支付通道是否启用（生产环境必须关闭）"));

        // 微信支付 — 默认全部空，等运营在 admin 后台配
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.WECHAT_ENABLED, "false", "payment",
                "微信支付总开关"));
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.WECHAT_APP_ID, "", "payment",
                "微信开放平台 AppID"));
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.WECHAT_MCH_ID, "", "payment",
                "微信支付商户号"));
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.WECHAT_API_V3_KEY, configCryptoService.encrypt(""), "payment",
                "微信支付 APIv3 密钥（加密存储）", true));
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.WECHAT_MCH_SERIAL_NO, "", "payment",
                "微信支付商户证书序列号"));
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.WECHAT_NOTIFY_URL, "", "payment",
                "微信支付回调 URL（外网可达，HTTPS）"));
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.WECHAT_MCH_PRIVATE_KEY, configCryptoService.encrypt(""), "payment",
                "微信支付商户 API 私钥（PEM 格式，加密存储）", true));
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.WECHAT_PLATFORM_CERT_PEM, configCryptoService.encrypt(""), "payment",
                "微信支付平台证书 PEM（回调验签用，加密存储；商户证书调 /v3/certificates 接口下载）", true));
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.WECHAT_SKIP_CALLBACK_SIGNATURE, "false", "payment",
                "开发模式跳过回调验签（生产环境严禁开启）"));

        // Apple IAP — 默认全部空，等申请到苹果开发者账号
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.APPLE_ENABLED, "false", "payment",
                "Apple IAP 总开关"));
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.APPLE_BUNDLE_ID, "", "payment",
                "Apple Bundle ID（如 com.lanprojects.fitcoach）"));
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.APPLE_SHARED_SECRET, configCryptoService.encrypt(""), "payment",
                "Apple App-Specific Shared Secret（加密存储）", true));
        inserted += ensureExists(new SysConfig(
                PaymentConfigKeys.APPLE_SANDBOX, "true", "payment",
                "是否走 Apple sandbox 环境（开发/审核 true，生产 false）"));

        if (inserted > 0) {
            log.info("[seeder] 支付配置初始化完成，新增 {} 项", inserted);
        }
    }

    private int ensureExists(SysConfig config) {
        if (sysConfigRepository.findByConfigKey(config.getConfigKey()).isEmpty()) {
            sysConfigRepository.save(config);
            return 1;
        }
        return 0;
    }
}
