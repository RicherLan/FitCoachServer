package com.lanprojects.fitcoach.login.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user", indexes = {
        @Index(name = "idx_open_id", columnList = "open_id"),
        @Index(name = "idx_union_id", columnList = "union_id")
})
public class User extends BaseEntity {

    /**
     * 用户唯一标识（UUID）
     */
    @Column(name = "uid", nullable = false, unique = true, length = 64)
    private String uid;

    /**
     * 昵称
     */
    @Column(name = "nickname", length = 100)
    private String nickname;

    /**
     * 头像 URL
     */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * 登录方式：WECHAT / PHONE / EMAIL / GUEST
     */
    @Column(name = "login_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private LoginType loginType;

    /**
     * 第三方平台 openid
     */
    @Column(name = "open_id", length = 100)
    private String openId;

    /**
     * 第三方平台 unionid（跨应用统一标识）
     */
    @Column(name = "union_id", length = 100)
    private String unionId;

    /**
     * 性别：0=未知, 1=男, 2=女
     */
    @Column(name = "gender")
    private Integer gender = 0;

    /**
     * 手机号（预留）
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * 邮箱（预留）
     */
    @Column(name = "email", length = 100)
    private String email;

    /**
     * 账号状态：true=正常, false=禁用
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /**
     * 最后登录时间
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 登录方式枚举
     */
    public enum LoginType {
        WECHAT,
        PHONE,
        EMAIL,
        GUEST
    }
}
