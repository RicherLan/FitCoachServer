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
     * 密码哈希（BCrypt 60 字符固定长度）。
     * <p>NULL 表示用户尚未设置密码 —— 微信 / OTP 登录用户首次登录时为空，
     * 走"账号安全 → 设置密码"才会写入。一旦设置后即可走密码登录入口。
     * <p>BCrypt 自带盐 + 自适应成本，不需要单独 salt 列。
     * @see com.lanprojects.fitcoach.login.config.PasswordEncoderConfig
     */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

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

    // ====== 单设备登录互踢 ======
    // 仅当客户端请求带真实 deviceId（ClientContext.get().hasDeviceId() == true）的登录才会写入；
    // admin 后台 / Postman / 早期未就绪窗口 的登录会保持 null，从而**不参与互踢**（向后兼容）。

    /**
     * 当前活跃 session 的 UUID。
     * <p>每次客户端登录（含同设备重登）都会生成新值并签入 JWT 的 {@code sid} claim。
     * <p>{@link com.lanprojects.fitcoach.login.service.AuthService#getCurrentUser(String)} 在校验 token 时
     * 比对 jwt.sid 与本字段，不一致即抛 {@link com.lanprojects.fitcoach.common.model.ResultCode#SESSION_KICKED}。
     * <p>null 表示"该用户暂无受互踢保护的活跃 session"——可能从未通过带 deviceId 的客户端登录过，
     * 此时所有未带 sid claim 的旧 token 都视为有效（向后兼容）。
     */
    @Column(name = "current_session_id", length = 64)
    private String currentSessionId;

    /**
     * 当前活跃 session 对应的设备 ID。
     * <p>登录时若 {@code !deviceId.equals(currentDeviceId)} 即视为"换设备登录"，会把 currentSessionId 翻新，
     * 从而把旧设备的 token 立即作废（旧设备下次请求时被踢）。
     * <p>同 deviceId 重登也会翻新 sessionId（旧 token 立即失效是 JWT 替换的自然结果），
     * 但用户没换设备无需 toast；客户端判断"是否换了设备"由旧设备收到 SESSION_KICKED 时自然触发。
     */
    @Column(name = "current_device_id", length = 64)
    private String currentDeviceId;

    /**
     * 当前 session 的发起时间，便于将来在被踢提示里展示"于 yyyy-MM-dd HH:mm 在另一设备登录"，
     * 也便于 admin 排查异地登录类客诉。
     */
    @Column(name = "current_login_at")
    private LocalDateTime currentLoginAt;

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
        APPLE,
        /**
         * 内部测试账号登录（仅 dev/staging 包 + sys_config 开关打开时启用）。
         * <p>通过 /api/auth/login/test 接口走用户名 + 密码校验，账号由 {@code DataInitializer}
         * 启动时 seed（uid 形如 {@code test_test1}）。生产环境必须关闭对应开关。
         */
        TEST
    }
}
