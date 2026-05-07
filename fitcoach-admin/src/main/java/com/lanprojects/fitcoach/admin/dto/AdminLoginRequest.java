package com.lanprojects.fitcoach.admin.dto;

import lombok.Data;

/**
 * 管理员登录请求体
 */
@Data
public class AdminLoginRequest {
    private String username;
    private String password;
}
