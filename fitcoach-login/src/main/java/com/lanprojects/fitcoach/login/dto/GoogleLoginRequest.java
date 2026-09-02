package com.lanprojects.fitcoach.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Google Sign In 登录请求（阶段 3B 波 2）。
 *
 * <p>客户端使用 Google Identity Services（RN 侧走
 * {@code @react-native-google-signin/google-signin}）完成用户交互，
 * 拿到 idToken 后调本接口 POST /api/auth/google/login。
 *
 * <p><b>与 Apple 的差异</b>：
 * <ul>
 *   <li>Google idToken 每次登录都携带完整的 email / name / picture，无需客户端在首次登录时
 *       额外把 email/name 传上来（保留字段仅作为兜底，正常不用）；</li>
 *   <li>Google 不像 Apple 那样对"隐藏邮箱"做默认操作，用户可在 Google 账户设置里
 *       选择公开或隐藏，我们只按 idToken 里返回的值处理。</li>
 * </ul>
 */
@Data
public class GoogleLoginRequest {

    /**
     * Google 返回的 idToken —— JWS 签名的 JWT，我们通过 {@code GoogleTokenVerifier} 校验签名 + 声明。
     * <p>Google idToken 一般 1200-1600 字符左右；上限 4096 兜底防止异常长度传入。
     */
    @NotBlank(message = "idToken 不能为空")
    @Size(max = 4096, message = "idToken 长度异常")
    private String idToken;

    /**
     * 客户端 SDK 生成的一次性 nonce，可用于回放攻击防御（当前预留，Server 端未强制校验）。
     * <p>若后续要求端到端校验：客户端签发 nonce 时应缓存本地一份，登录成功后立即失效；
     * Server 端需与该 nonce 做匹配核对。
     */
    @Size(max = 128, message = "nonce 长度超过限制")
    private String nonce;
}
