package com.lanprojects.fitcoach.login.dto;

/**
 * Apple identityToken 验签通过后，服务端关心的字段集合（内部 DTO，不出网络）。
 *
 * <p>字段语义摘自 Apple <a href="https://developer.apple.com/documentation/signinwithapplerestapi/authenticating_users_with_sign_in_with_apple">
 * Sign In with Apple REST API 文档</a>：
 * <ul>
 *   <li>{@link #sub}   —— 用户在该 App（同 Team）下的唯一 ID，终身不变，作为服务端识别用户的**唯一主键**；</li>
 *   <li>{@link #email} —— 用户邮箱；仅当客户端本次登录时用户同意授权 email scope，且不是"用户曾拒绝过再重授"
 *                       的场景下才可能出现（**不可靠**，只能作为参考）；</li>
 *   <li>{@link #emailVerified} —— email 是否已被 Apple 验证；服务端可结合 {@link #isPrivateEmail} 判断是否走隐藏转发；</li>
 *   <li>{@link #isPrivateEmail} —— 是否是 Apple 隐藏邮箱转发（xxx@privaterelay.appleid.com）；</li>
 *   <li>{@link #audience} —— identityToken 的 aud claim，服务端校验时会匹配到某个允许的 Bundle ID / Services ID。</li>
 * </ul>
 *
 * <p><b>不导出网络</b>：本 DTO 仅在 fitcoach-login 内部使用（AppleTokenVerifier → AppleService → AuthService），
 * 不作为 API 响应体外发；对客户端只暴露 LoginResponse。
 */
public record AppleIdTokenPayload(
        String sub,
        String email,
        Boolean emailVerified,
        Boolean isPrivateEmail,
        String audience
) {
    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
