package com.lanprojects.fitcoach.login.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信登录请求 — 客户端传来微信授权码
 */
@Data
public class WeChatLoginRequest {

    /**
     * 微信授权码（客户端从微信 SDK 获取）
     */
    @NotBlank(message = "微信授权码不能为空")
    private String code;
}
