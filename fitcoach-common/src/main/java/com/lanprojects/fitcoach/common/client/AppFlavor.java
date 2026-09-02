package com.lanprojects.fitcoach.common.client;

/**
 * App 编译期市场标识 —— 与 RN 端 {@code src/common/flavor/types.ts} 的 {@code AppFlavor} 完全对齐。
 *
 * <p><b>核心概念</b>：Flavor 是"这台装机上安装的是哪个市场的 App"，编译期由客户端打包工具链写死
 * （Android buildConfig / iOS Info.plist），HTTP 请求通过 {@code X-App-Flavor} header 上报，
 * 服务端在 {@link ClientInfoInterceptor} 解析后放进 {@link ClientContext}。
 *
 * <p><b>与 User Region 的区别</b>（重要，别混）：
 * <ul>
 *   <li>Flavor：<b>包属性</b>，编译期常量，一台装机永不变；决定<b>能力集</b>（登录方式/支付通道/合规文案）；</li>
 *   <li>Region：<b>用户属性</b>，运行时可变；决定<b>运营策略</b>（价格/推荐/UI 语言）。</li>
 * </ul>
 * 二者独立演进，只在极特殊场景重叠（如国内包不允许海外账号登录，见阶段 3）。
 *
 * <p><b>取值来源</b>：
 * <ul>
 *   <li>HTTP header {@code X-App-Flavor} — 客户端逐请求上报（{@link ClientInfoInterceptor#HDR_APP_FLAVOR}）；</li>
 *   <li>{@code user.register_flavor} — 用户首次注册时锁定，此后永不变更（用于运营分析、跨设备身份追溯）。</li>
 * </ul>
 *
 * <p><b>缺失兜底</b>：非 RN 客户端（admin 后台 / Postman / 老版本客户端）不带 header 时，
 * 服务端字段为 {@code null}，业务侧自行判 null（不要"假设 CN"或"假设 GLOBAL"，会掩盖真实 flavor 缺失）。
 */
public enum AppFlavor {
    /** 国内包：微信/Apple/手机号登录，微信/支付宝/IAP 支付，ICP + 公安备案 */
    CN,

    /** 海外包：Google/Apple/邮箱登录，Play Billing/IAP/Stripe 支付，GDPR/CCPA 合规 */
    GLOBAL;

    /**
     * 从 HTTP header 字符串解析为枚举，非法值或空返回 {@code null}（<b>不做默认兜底</b>）。
     *
     * <p>为什么不兜底：flavor 缺失的场景（admin/Postman/老客户端）业务上有明确语义（"不是 RN 客户端发的"），
     * 静默兜底会掩盖"客户端漏配 header"这种真实 bug。
     *
     * @param raw HTTP header 原始值，允许 null 与非法值
     * @return 匹配的枚举，或 {@code null}（缺失/非法）
     */
    public static AppFlavor parse(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        // 严格大小写匹配：客户端契约就是大写 "CN" / "GLOBAL"，容忍其他格式反而会掩盖客户端的 bug
        try {
            return AppFlavor.valueOf(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
