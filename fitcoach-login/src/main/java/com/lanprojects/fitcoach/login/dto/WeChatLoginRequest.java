package com.lanprojects.fitcoach.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 微信登录请求 — 客户端传来微信授权码
 */
@Data
public class WeChatLoginRequest {

    /**
     * 微信授权码（客户端从微信 SDK 获取）
     * <p>
     * 微信官方 code 长度通常 32~64，给出宽松上限避免被恶意超长字符串撑爆日志。
     */
    @NotBlank(message = "微信授权码不能为空")
    @Size(min = 1, max = 1024, message = "微信授权码长度不合法")
    private String code;
}
