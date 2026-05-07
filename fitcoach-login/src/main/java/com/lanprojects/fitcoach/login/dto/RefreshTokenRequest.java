package com.lanprojects.fitcoach.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 刷新 access token 请求
 * <p>
 * 客户端在 access token 即将过期或已过期时，用 refresh token 换取新的 access token。
 */
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "refreshToken 不能为空")
    @Size(min = 1, max = 4096, message = "refreshToken 长度不合法")
    private String refreshToken;
}
