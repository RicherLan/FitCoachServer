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
        @Index(name = "uk_open_id", columnList = "open_id", unique = true),
        @Index(name = "uk_union_id", columnList = "union_id", unique = true),
        // phone 允许 NULL；MySQL 上 unique 索引允许多个 NULL，因此微信登录用户的 phone=null 不会冲突
        @Index(name = "uk_phone", columnList = "phone", unique = true)
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
     * 登录方式：WECHAT / PHONE / EMAIL / GUEST / GOOGLE / APPLE
     * <p>注意：表示"账号注册时的首选登录方式"，用户后续可能绑定多种登录方式。
     */
    @Column(name = "login_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private LoginType loginType;

    /**
     * 第三方平台 openid
     * <p>
     * unique 约束在 {@link Table#indexes()} 上声明，避免并发登录创建多条记录。
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
     * 手机号（unique，用于手机号验证码登录）。
     * <p>NULL 允许并存，因此微信用户未绑定手机号时也能正常存。
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
     * 最后活跃时间（用户客户端最近一次发起业务请求的时间）
     * <p>由 {@link com.lanprojects.fitcoach.login.service.UserActivityService#touch(String)} 更新，
     * 接入点是 fitcoach-clientbus 模块的客户端通用轮询入口（GET /api/client/poll，120s 周期）；
     * 该入口聚合了所有未来"服务端推、客户端拉"能力，所以无需在每个业务接口里各自调 touch()。
     * <p>用于 admin 后台显示"在线/离线"：now - lastActiveAt < 5min 视为在线
     * （给 120s 轮询周期 2.5 倍冗余，避免一次轮询丢失就被判离线）。
     */
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    /**
     * 登录方式枚举
     * <p>新增登录方式时只需在此处加值 + 实现对应 Service/Controller，
     * 数据库列已通过 {@code @Enumerated(EnumType.STRING)} 兼容新值。
     */
    public enum LoginType {
        WECHAT,
        PHONE,
        EMAIL,
        GUEST,
        GOOGLE,
        APPLE
    }
}
