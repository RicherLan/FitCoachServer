package com.lanprojects.fitcoach.payment.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.payment.entity.PaymentChannel;
import com.lanprojects.fitcoach.payment.provider.PaymentChannelProvider;
import com.lanprojects.fitcoach.payment.provider.PaymentConfigKeys;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 通道路由器 — 解决两件事：
 * <ol>
 *   <li>按 {@link PaymentChannel} 选具体的 Provider 实现（启动时把所有 PaymentChannelProvider Bean 收集成 map）；</li>
 *   <li>按客户端平台「自动决策」该走哪个通道（Mock 优先、iOS 走 Apple、Android 走 WeChat）。</li>
 * </ol>
 *
 * <p>iOS 必须走 Apple IAP 是<b>苹果商店强制规定</b>（虚拟商品禁止走第三方支付），违规会被下架。
 * Android 国内端走微信，海外预留 Google Play。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentChannelRouter {

    private final List<PaymentChannelProvider> providers;
    private final SysConfigService sysConfigService;

    /** 通道 → Provider 实例的查找表（启动时一次性构建） */
    private final Map<PaymentChannel, PaymentChannelProvider> channelMap = new EnumMap<>(PaymentChannel.class);

    @PostConstruct
    void initChannelMap() {
        for (PaymentChannelProvider p : providers) {
            channelMap.put(p.channel(), p);
            log.info("[payment] 注册支付通道 {} → {}", p.channel(), p.getClass().getSimpleName());
        }
    }

    /**
     * 拿指定通道的 Provider，做可用性校验。
     */
    public PaymentChannelProvider require(PaymentChannel channel) {
        PaymentChannelProvider provider = channelMap.get(channel);
        if (provider == null) {
            throw new BusinessException(ResultCode.PAYMENT_CHANNEL_NOT_AVAILABLE,
                    "未注册的支付通道：" + channel);
        }
        if (!provider.isAvailable()) {
            throw new BusinessException(ResultCode.PAYMENT_CHANNEL_DISABLED,
                    "支付通道暂不可用：" + channel + "（请联系管理员配置）");
        }
        return provider;
    }

    /**
     * 按客户端平台决策默认通道：
     * <ul>
     *   <li>{@code payment.mock.enabled=true} 时优先 MOCK（开发期）；</li>
     *   <li>iOS → APPLE_IAP（苹果商店强制要求虚拟商品走 IAP）；</li>
     *   <li>Android → WECHAT；</li>
     *   <li>其他/未知（admin 后台测试） → 抛 PAYMENT_PLATFORM_REQUIRED。</li>
     * </ul>
     *
     * @param clientPlatform "android" / "ios" / null
     */
    public PaymentChannel resolveDefault(String clientPlatform) {
        if (sysConfigService.getBoolValue(PaymentConfigKeys.MOCK_ENABLED, false)) {
            return PaymentChannel.MOCK;
        }
        if ("ios".equalsIgnoreCase(clientPlatform)) {
            return PaymentChannel.APPLE_IAP;
        }
        if ("android".equalsIgnoreCase(clientPlatform)) {
            return PaymentChannel.WECHAT;
        }
        throw new BusinessException(ResultCode.PAYMENT_PLATFORM_REQUIRED);
    }
}
