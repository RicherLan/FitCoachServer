package com.lanprojects.fitcoach.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * /api/auth/login/account 请求体 —— 「账号 + 密码」登录。
 *
 * <p>account 是 user 的内在唯一标识（{@link com.lanprojects.fitcoach.login.entity.User#getAccount()}，
 * 8 位纯数字），登录失败统一回 {@code PASSWORD_LOGIN_FAILED}，避免泄露
 * "账号是否存在 / 是否设置过密码"。
 */
@Data
public class AccountLoginRequest {

    /** 用户号 —— 服务端生成，8 位纯数字 + 首位 1-9；为兼容历史可能的格式扩展，最长 16 位。 */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{1,16}$", message = "账号格式不正确")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 64, message = "密码长度不合法")
    private String password;
}
