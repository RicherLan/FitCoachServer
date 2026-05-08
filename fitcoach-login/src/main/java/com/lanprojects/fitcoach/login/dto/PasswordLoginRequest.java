package com.lanprojects.fitcoach.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * /api/auth/login/password 请求体
 * <p>密码格式校验在 service 层做（与"设置密码"接口共用同一份规则），
 * 这里只校验"非空 + 长度上限"，避免登录时把"密码格式"与"密码错误"两个错误码区分提示
 * 反而给暴力枚举提供信号 —— 长度越界直接走前置 @Valid，server 永远统一回"账号或密码错误"。
 */
@Data
public class PasswordLoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 64, message = "密码长度不合法")
    private String password;
}
