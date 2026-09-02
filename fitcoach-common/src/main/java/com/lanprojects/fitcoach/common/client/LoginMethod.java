package com.lanprojects.fitcoach.common.client;

/**
 * 登录方式枚举 —— 与客户端 flavorProfile.loginMethods 数组元素一一对应（客户端小写、服务端大写）。
 *
 * <p><b>用途</b>：作为 {@link FlavorLoginPolicy} 白名单的 key，用于服务端在 AuthController
 * 各登录端点入口做"当前 flavor 是否允许该登录方式"的校验；同时也可作为 user.registerMethod
 * 字段的候选值（如未来需要区分"用户是通过哪种方式首次注册的"，可复用此枚举）。
 *
 * <p><b>与 URL path 的映射关系</b>：
 * <ul>
 *   <li>{@link #WECHAT}  → POST /api/auth/wechat/login</li>
 *   <li>{@link #PHONE}   → POST /api/auth/phone/sendCode + /phone/login + /login/password
 *       （password 登录用手机号做用户名，本质上仍属"手机号家族"）</li>
 *   <li>{@link #ACCOUNT} → POST /api/auth/login/account</li>
 *   <li>{@link #GOOGLE}  → POST /api/auth/google/login（阶段 3B 落地）</li>
 *   <li>{@link #APPLE}   → POST /api/auth/apple/login（阶段 3B 落地）</li>
 *   <li>{@link #EMAIL}   → POST /api/auth/email/* （尚未启用）</li>
 * </ul>
 *
 * <h3>加值规范</h3>
 * <ol>
 *   <li>先在此加枚举 + JavaDoc；</li>
 *   <li>再在 {@link FlavorLoginPolicy} 里的 {@code CN_METHODS} / {@code GLOBAL_METHODS}
 *       两处白名单明确"哪些 flavor 允许该方式"；</li>
 *   <li>最后在对应 AuthController 端点入口调 {@link FlavorLoginPolicy#ensureAllowed(LoginMethod)}。</li>
 * </ol>
 */
public enum LoginMethod {

    /** 微信授权码登录（仅 CN flavor） */
    WECHAT,

    /**
     * 手机号 + 短信验证码 / 手机号 + 密码 —— 归属同一"家族"。
     * <p>因为 password 登录复用 user.phone 字段做用户名，其能力可用性完全取决于手机号是否已绑定，
     * 所以在 flavor 白名单层面视为同一种登录方式，不再细分。
     */
    PHONE,

    /**
     * 用户号 + 密码 —— <b>全 flavor 通用</b>的账号内在凭证。
     * <p>account 是 user 的内在唯一标识，无论用户当初是通过哪种方式注册（微信 / 手机号 / Google / Apple），
     * 只要在"账号安全"里设置过密码即可走此入口。因此 {@link FlavorLoginPolicy} 对 ACCOUNT 一律放行，
     * 不受 CN/GLOBAL 白名单约束。
     */
    ACCOUNT,

    /** Google 授权登录（仅 GLOBAL flavor，阶段 3B 接入 Firebase / Google Identity Services） */
    GOOGLE,

    /** Apple 授权登录（仅 GLOBAL flavor，阶段 3B 接入 Sign In with Apple） */
    APPLE,

    /** 邮箱 + OTP（仅 GLOBAL flavor，尚未启用） */
    EMAIL,
}
