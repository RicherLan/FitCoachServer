package com.lanprojects.fitcoach.admin.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 管理员登录返回体（含 token + 个人资料）
 */
@Data
@Builder
public class AdminLoginResponse {
    private String username;
    private String displayName;
    private String role;
    /** access token */
    private String token;
    /** access token 过期秒数 */
    private long expiresIn;
}
