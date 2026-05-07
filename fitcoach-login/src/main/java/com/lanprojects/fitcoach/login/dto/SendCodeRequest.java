package com.lanprojects.fitcoach.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * /api/auth/phone/sendCode 请求体
 * <p>
 * scene 是为后续多场景预留（登录 / 注册 / 改密码），首版仅支持 LOGIN。
 */
@Data
public class SendCodeRequest {

    /** 11 位国内手机号。海外号后续单独扩展。 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 业务场景：LOGIN（默认）/ REGISTER / RESET_PWD。当前只用 LOGIN，预留扩展。 */
    private String scene = "LOGIN";
}
