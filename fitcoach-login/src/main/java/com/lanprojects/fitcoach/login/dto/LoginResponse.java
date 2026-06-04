package com.lanprojects.fitcoach.login.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功响应 — 返回用户信息 + JWT token + refresh token
 * <p>
 * - 客户端持久化 token / refreshToken / 时间戳；
 * - access token 短期有效（默认 2h），过期后用 refreshToken 调 /api/auth/refresh 换新；
 * - /api/auth/me 接口只返回基础信息（不会再返回 token / refreshToken / expiresIn / refreshExpiresIn）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * 用户唯一标识（UUID，内部主键）
     */
    private String uid;

    /**
     * 用户号（{@link com.lanprojects.fitcoach.login.entity.User#account}）—— 8 位纯数字，
     * 用户对外展示的"账号"，可用于「账号 + 密码」登录、客服查询、好友搜索。
     */
    private String account;

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
     * JWT access token（短期）
     */
    private String token;

    /**
     * access token 过期时间（秒）
     */
    private Long expiresIn;

    /**
     * refresh token（长期，用来换新的 access token）
     */
    private String refreshToken;

    /**
     * refresh token 过期时间（秒）
     */
    private Long refreshExpiresIn;

    /**
     * 账号创建时间（毫秒时间戳）
     */
    private Long createTime;

    /**
     * 最后登录时间（毫秒时间戳）
     */
    private Long lastLoginTime;
}
