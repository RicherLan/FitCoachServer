package com.lanprojects.fitcoach.payment.service;

import com.lanprojects.fitcoach.common.client.AppFlavor;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通道路由器 — 解决三件事：
 * <ol>
 *   <li>按 {@link PaymentChannel} 选具体的 Provider 实现（启动时把所有 PaymentChannelProvider Bean 收集成 map）；</li>
 *   <li>按 flavor + 客户端平台「自动决策」该走哪个通道（阶段 4 前只按平台）；</li>
 *   <li>校验客户端指定的通道是否在 {@code (flavor × platform)} 白名单矩阵内（阶段 4 新增）。</li>
 * </ol>
 *
 * <p><b>白名单矩阵</b>（阶段 4）：
 * <table border="1">
 *   <tr><th>Flavor</th><th>Platform</th><th>允许的 PaymentChannel</th></tr>
 *   <tr><td>CN</td><td>iOS</td><td>APPLE_IAP</td></tr>
 *   <tr><td>CN</td><td>Android</td><td>WECHAT, MOCK</td></tr>
 *   <tr><td>GLOBAL</td><td>iOS</td><td>APPLE_IAP</td></tr>
 *   <tr><td>GLOBAL</td><td>Android</td><td>GOOGLE_PLAY, MOCK</td></tr>
 * </table>
 *
 * <p><b>iOS 必须走 Apple IAP</b> 是苹果商店强制规定（虚拟商品禁止走第三方支付），违规会被下架。
 *
 * <p><b>Flavor 缺失兜底</b>：{@code appFlavor == null}（admin/Postman/老客户端）时跳过 flavor 白名单校验，
 * 仅按 platform 路由；避免掩盖真实的"客户端漏配 header" bug 的同时保证内部工具可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentChannelRouter {

    private final List<PaymentChannelProvider> providers;
    private final SysConfigService sysConfigService;

    /** 通道 → Provider 实例的查找表（启动时一次性构建） */
    private final Map<PaymentChannel, PaymentChannelProvider> channelMap = new EnumMap<>(PaymentChannel.class);

    /**
     * Flavor × Platform 白名单矩阵 —— key 拼接为 {@code "<FLAVOR>:<platform>"}（platform 小写），
     * value 为该组合下允许的支付通道集合。
     *
     * <p>使用 EnumSet 而非 List，是为了在校验时 {@code contains} 走 O(1) bitmap 判断，
     * 同时通过枚举安全避免拼写错误。
     *
     * <p>MOCK 通道无条件加入国内/海外 Android，是因为 {@code payment.mock.enabled=true} 开发期
     * 需要 Android 双 flavor 都能走通；生产环境靠 {@link PaymentConfigKeys#MOCK_ENABLED} 关闭。
     */
    private static final Map<String, Set<PaymentChannel>> FLAVOR_PLATFORM_WHITELIST = Map.of(
            "CN:ios",         EnumSet.of(PaymentChannel.APPLE_IAP),
            "CN:android",     EnumSet.of(PaymentChannel.WECHAT, PaymentChannel.MOCK),
            "GLOBAL:ios",     EnumSet.of(PaymentChannel.APPLE_IAP),
            "GLOBAL:android", EnumSet.of(PaymentChannel.GOOGLE_PLAY, PaymentChannel.MOCK)
    );

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
     * 按 flavor + 客户端平台决策默认通道：
     * <ul>
     *   <li>{@code payment.mock.enabled=true} 时优先 MOCK（开发期，任意 flavor/platform 通吃）；</li>
     *   <li>iOS → APPLE_IAP（苹果商店强制要求虚拟商品走 IAP，两个 flavor 都一样）；</li>
     *   <li>CN Android → WECHAT；</li>
     *   <li>GLOBAL Android → GOOGLE_PLAY；</li>
     *   <li>flavor 缺失且 Android → 保底走 WECHAT（历史兼容，admin/Postman 场景）；</li>
     *   <li>其他/未知 → 抛 PAYMENT_PLATFORM_REQUIRED。</li>
     * </ul>
     *
     * @param clientPlatform "android" / "ios" / null
     * @param appFlavor      CN / GLOBAL / null（非 RN 客户端）
     */
    public PaymentChannel resolveDefault(String clientPlatform, AppFlavor appFlavor) {
        if (sysConfigService.getBoolValue(PaymentConfigKeys.MOCK_ENABLED, false)) {
            return PaymentChannel.MOCK;
        }
        if ("ios".equalsIgnoreCase(clientPlatform)) {
            return PaymentChannel.APPLE_IAP;
        }
        if ("android".equalsIgnoreCase(clientPlatform)) {
            if (appFlavor == AppFlavor.GLOBAL) {
                return PaymentChannel.GOOGLE_PLAY;
            }
            // CN flavor 或 flavor 缺失（admin/Postman/老客户端）均走微信 —— 保持向后兼容
            return PaymentChannel.WECHAT;
        }
        throw new BusinessException(ResultCode.PAYMENT_PLATFORM_REQUIRED);
    }

    /**
     * Flavor × Platform × PaymentChannel 白名单校验 —— 阶段 4 新增。
     *
     * <p><b>触发时机</b>：{@link PaymentService#createOrder(CreateOrderCommand)} 在路由到具体
     * Provider 前立即调用，防止 CN 包调 GOOGLE_PLAY、GLOBAL 包调 WECHAT 等越权支付。
     *
     * <p><b>缺失兜底</b>：{@code appFlavor == null} 时跳过校验（admin 后台/Postman/老客户端），
     * 依赖 {@link #require(PaymentChannel)} 做基础通道可用性兜底。此设计保持与
     * {@link com.lanprojects.fitcoach.common.client.ClientContext#appFlavor()} 的语义一致
     * ——"缺失有明确语义（不是 RN 客户端），不做假设"。
     *
     * <p><b>与 8101 的区别</b>：
     * <ul>
     *   <li>{@code PAYMENT_CHANNEL_NOT_AVAILABLE}（8101）：通道未注册/未配置的运行时可用性问题；</li>
     *   <li>{@code PAYMENT_CHANNEL_NOT_ALLOWED_FOR_FLAVOR}（8114）：通道在编译期 flavor 白名单外的市场归属问题。</li>
     * </ul>
     *
     * @param channel  客户端请求的支付通道
     * @param appFlavor 客户端 App Flavor（可为 null）
     * @param clientPlatform 客户端平台 "android" / "ios"
     * @throws BusinessException 8114 如果 channel 不在 (flavor, platform) 白名单
     */
    public void validateChannelAllowed(PaymentChannel channel, AppFlavor appFlavor, String clientPlatform) {
        if (appFlavor == null) {
            // 非 RN 客户端（admin/Postman/老客户端），跳过 flavor 维度校验
            return;
        }
        if (clientPlatform == null || clientPlatform.isBlank()) {
            // 有 flavor 却没 platform，属于契约异常
            throw new BusinessException(ResultCode.PAYMENT_PLATFORM_REQUIRED);
        }
        String key = appFlavor.name() + ":" + clientPlatform.toLowerCase();
        Set<PaymentChannel> allowed = FLAVOR_PLATFORM_WHITELIST.get(key);
        if (allowed == null || !allowed.contains(channel)) {
            log.warn("[payment] 通道 {} 不在白名单：flavor={} platform={} 允许集={}",
                    channel, appFlavor, clientPlatform, allowed);
            throw new BusinessException(ResultCode.PAYMENT_CHANNEL_NOT_ALLOWED_FOR_FLAVOR,
                    "通道 " + channel + " 不在 " + appFlavor + "/" + clientPlatform + " 白名单");
        }
    }
}
