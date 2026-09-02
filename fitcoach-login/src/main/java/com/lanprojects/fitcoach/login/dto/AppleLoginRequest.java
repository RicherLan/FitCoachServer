package com.lanprojects.fitcoach.login.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Apple Sign In 登录请求体（阶段 3B 波 1）。
 *
 * <p><b>字段来源</b>：来自 RN 端 {@code @invertase/react-native-apple-authentication} SDK 的
 * {@code performRequest()} 返回的 {@code AppleAuthRequestResponse}：
 * <ul>
 *   <li>{@link #identityToken} = response.identityToken —— Apple 签发的 JWT，包含 sub / email / iss / aud / exp；</li>
 *   <li>{@link #email}         = response.email        —— 仅**首次授权**返回，可能是隐藏邮箱；</li>
 *   <li>{@link #fullName}      = response.fullName     —— 仅**首次授权**返回，且用户可勾选"隐藏姓名"导致为 null；</li>
 *   <li>{@link #nonce}         = response.nonce        —— 客户端生成的随机数，可选，用于防重放（当前未在服务端校验，预留）。</li>
 * </ul>
 *
 * <p><b>服务端处理</b>：
 * <ol>
 *   <li>identityToken 是唯一的**身份凭证** —— 必须走 Apple JWK 验签 + iss/aud/exp 校验；</li>
 *   <li>email / fullName 只是**首次登录的附加元数据** —— 不用于身份识别，仅用作首次注册时的资料填充；
 *       同一 user 后续再登录时 email / fullName 通常为 null，服务端不覆盖已存字段；</li>
 *   <li>nonce 当前保留但不校验 —— 未来若接入 Web / macOS Sign In 支持时再补上防重放。</li>
 * </ol>
 */
@Data
@Schema(description = "Apple Sign In 登录请求")
public class AppleLoginRequest {

    /**
     * Apple 签发的身份凭证 JWT（identityToken）。
     * <p>由 Apple 用 ES256 / RS256 签名（当前统一 RS256），服务端下载 Apple JWK 公钥后本地验签。
     * <p>典型长度 800-1200 字符，upper bound 给 4096 兜底。
     */
    @NotBlank(message = "identityToken 不能为空")
    @Size(max = 4096, message = "identityToken 长度超过 4096 字符上限，疑似非法请求")
    @Schema(description = "Apple identityToken（JWT）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identityToken;

    /**
     * 首次授权返回的邮箱，仅首次登录不为空。
     * <p>可能是真实邮箱，也可能是 Apple 隐藏邮箱转发地址（xxx@privaterelay.appleid.com）。
     */
    @Size(max = 200, message = "email 长度超过 200 字符上限")
    @Schema(description = "首次授权返回的邮箱（仅首次不为空）")
    private String email;

    /**
     * 首次授权返回的用户全名，仅首次登录不为空；用户勾选"隐藏姓名"时也为 null。
     * <p>RN SDK 返回结构是 {@code AppleAuthUserName{givenName, familyName}}，
     * 建议客户端在发送前先拼接为 "given family" 字符串。
     */
    @Size(max = 200, message = "fullName 长度超过 200 字符上限")
    @Schema(description = "首次授权返回的用户全名（\"given family\" 拼接后）")
    private String fullName;

    /**
     * 客户端生成的 nonce（可选，防重放预留）。
     * <p>当前服务端不做校验；未来支持 Web/macOS Sign In 时接入。
     */
    @Size(max = 128, message = "nonce 长度超过 128 字符上限")
    @Schema(description = "客户端生成的 nonce（预留，可选）")
    private String nonce;
}
