package com.lanprojects.fitcoach.login.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功响应 — 返回用户信息 + JWT token
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * 用户唯一标识
     */
    private String uid;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像 URL
     */
    private String avatarUrl;

    /**
     * 性别：0=未知, 1=男, 2=女
     */
    private Integer gender;

    /**
     * 登录方式
     */
    private String loginType;

    /**
     * JWT access token
     */
    private String token;

    /**
     * token 过期时间（秒）
     */
    private Long expiresIn;
}
