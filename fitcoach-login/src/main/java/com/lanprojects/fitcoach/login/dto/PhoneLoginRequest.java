package com.lanprojects.fitcoach.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * /api/auth/phone/login 请求体
 */
@Data
public class PhoneLoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 4-8 位数字验证码；OtpService 默认 6 位，留弹性以适配未来短信模板调整 */
    @NotBlank(message = "验证码不能为空")
    @Size(min = 4, max = 8, message = "验证码长度不合法")
    @Pattern(regexp = "^\\d+$", message = "验证码必须是数字")
    private String code;
}
