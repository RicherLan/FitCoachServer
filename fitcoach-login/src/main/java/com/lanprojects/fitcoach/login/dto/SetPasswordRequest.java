package com.lanprojects.fitcoach.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * /api/user/password 设置 / 修改密码请求体。
 * <p>规则（service 层 PasswordService 校验）：
 * <ul>
 *     <li>未设置密码：必须提供 {@code otpCode}（先调 /api/auth/phone/sendCode）；</li>
 *     <li>已设置密码：{@code oldPassword} 与 {@code otpCode} 二选一，便于忘记旧密码用户走 OTP 路径。</li>
 * </ul>
 * <p>密码强度仅在 service 层校验，DTO 层只做"非空 + 长度上限"，避免泄露过多规则细节。
 */
@Data
public class SetPasswordRequest {

    /** 新密码（明文走 HTTPS；server 落库前 BCrypt 哈希） */
    @NotBlank(message = "新密码不能为空")
    @Size(max = 64, message = "密码长度不合法")
    private String newPassword;

    /** 旧密码（已设置密码时与 otpCode 二选一） */
    @Size(max = 64, message = "旧密码长度不合法")
    private String oldPassword;

    /** 短信验证码（未设置密码时必填；已设置时与 oldPassword 二选一） */
    @Size(min = 4, max = 8, message = "验证码长度不合法")
    private String otpCode;
}
