package com.lanprojects.fitcoach.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * /api/auth/phone/sendCode 请求体
 * <p>
 * scene 是为后续多场景预留（登录 / 注册 / 改密码），首版仅支持 LOGIN。
 * <p>
 * captchaTicket / captchaRandstr 用于腾讯行为验证码校验：
 * 前端弹出 CAPTCHA → 用户通过 → 拿到 ticket + randstr → 随 sendCode 一并提交。
 */
@Data
public class SendCodeRequest {

    /** 11 位国内手机号。海外号后续单独扩展。 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 业务场景：LOGIN（默认）/ REGISTER / RESET_PWD。当前只用 LOGIN，预留扩展。 */
    private String scene = "LOGIN";

    /** 腾讯验证码 ticket（前端 CAPTCHA 通过后获得） */
    private String captchaTicket;

    /** 腾讯验证码随机字符串（前端 CAPTCHA 通过后获得，与 ticket 配对） */
    private String captchaRandstr;
}
