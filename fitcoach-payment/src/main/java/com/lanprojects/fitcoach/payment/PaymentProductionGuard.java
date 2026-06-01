package com.lanprojects.fitcoach.payment;

import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.payment.provider.PaymentConfigKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 支付模块生产环境安全守卫 —— 启动完成后扫一遍 sys_config，
 * 阻断"运维误配将开发期开关带到生产"造成的资损 / 安全风险。
 *
 * <p>检查矩阵：
 * <ul>
 *   <li>{@code payment.mock.enabled=true} + active profile=prod → fail-fast 启动失败</li>
 *   <li>{@code payment.wechat.skipCallbackSignature=true} + active profile=prod → fail-fast 启动失败</li>
 * </ul>
 *
 * <p>用 {@link ApplicationReadyEvent} 而非 {@link jakarta.annotation.PostConstruct}：
 * SysConfigService 在 PostConstruct 阶段不一定 ready（依赖 DataSource + Cache），
 * ApplicationReadyEvent 时整个 spring 上下文已就绪，读 DB 配置最稳。
 *
 * <p>失败策略：直接抛 IllegalStateException 让进程退出，运维收到启动失败比"上线后被绕过支付"
 * 友好得多。运维需先到 admin 后台关掉开关再启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProductionGuard {

    /** 视为"生产"的 spring profile 名字（小写比较）。"prod" / "production" 都算。 */
    private static final List<String> PROD_PROFILE_NAMES = Arrays.asList("prod", "production");

    private final Environment environment;
    private final SysConfigService sysConfigService;

    @EventListener(ApplicationReadyEvent.class)
    public void verifyPaymentSafety() {
        boolean isProd = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(PROD_PROFILE_NAMES::contains);

        if (!isProd) {
            log.info("[payment-guard] 非生产环境（profiles={}），跳过支付安全开关检查",
                    Arrays.toString(environment.getActiveProfiles()));
            return;
        }

        boolean mockEnabled = sysConfigService.getBoolValue(PaymentConfigKeys.MOCK_ENABLED, false);
        boolean skipSig = sysConfigService.getBoolValue(
                PaymentConfigKeys.WECHAT_SKIP_CALLBACK_SIGNATURE, false);

        if (mockEnabled) {
            throw new IllegalStateException(
                    "❌ 生产环境严禁开启 Mock 支付（" + PaymentConfigKeys.MOCK_ENABLED
                            + "=true）！请到 admin 后台关闭后重启。");
        }
        if (skipSig) {
            throw new IllegalStateException(
                    "❌ 生产环境严禁跳过微信回调签名校验（"
                            + PaymentConfigKeys.WECHAT_SKIP_CALLBACK_SIGNATURE
                            + "=true）！请到 admin 后台关闭后重启。");
        }

        log.info("[payment-guard] 生产环境支付安全开关检查通过 mockEnabled={} skipCallbackSignature={}",
                mockEnabled, skipSig);
    }
}
