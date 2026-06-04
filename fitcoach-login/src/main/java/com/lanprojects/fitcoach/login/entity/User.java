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
        @Index(name = "uk_phone", columnList = "phone", unique = true),
        // account：用户的内在唯一标识（类似小红书号），所有登录方式（微信/手机/Google/Apple/账号密码）
        // 都最终落到一行 user 上；admin 后台、客服查询、未来好友搜索均以 account 为入口
        @Index(name = "uk_account", columnList = "account", unique = true)
})
public class User extends BaseEntity {

    /**
     * 用户唯一标识（UUID）—— 内部主键属性，不暴露给终端用户。
     * <p>所有外部展示 / 用户输入的"用户号"统一使用 {@link #account} 字段。
     */
    @Column(name = "uid", nullable = false, unique = true, length = 64)
    private String uid;

    /**
     * 用户号（类似小红书号）：8 位纯数字、首位 1-9、全局唯一、终身不变。
     * <p>由 {@code AccountGenerator} 在用户首次注册时（无论何种登录方式）随机生成。
     * <p>用途：
     * <ul>
     *   <li>「账号 + 密码」登录入口的账号；密码由用户在「账号安全 → 设置密码」中自行设置。</li>
     *   <li>admin 后台 / 客服查询用户的主键。</li>
     *   <li>未来好友 / 教练搜索的对外标识。</li>
     * </ul>
     * <p>注意：新注册自动生成、不允许用户修改、不可重复使用。
     */
    @Column(name = "account", length = 16, unique = true)
    private String account;

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
     * 最近一次成功登录使用的登录方式：WECHAT / PHONE / EMAIL / GOOGLE / APPLE / ACCOUNT / GUEST。
     * <p>每次登录成功时由 {@code AuthService} 更新；用于 admin 后台展示与运营分析，
     * 不参与业务路由（同一 user 可同时绑定多种登录方式）。
     * <p>用户的"内在身份"由 {@link #account} 承载，与本字段独立。
     */
    @Column(name = "login_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private LoginType loginType;

    /**
     * 注册来源（首次创建 user 的登录方式 / 渠道），与 {@link #loginType} 不同，本字段一旦写入永不变更。
     * <p>用于运营分析（如"通过微信注册的用户占比"），以及客服追溯账号来源。
     */
    @Column(name = "registration_source", length = 20)
    @Enumerated(EnumType.STRING)
    private RegistrationSource registrationSource;

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
     * 登录方式枚举 —— 表示「最近一次成功登录使用的方式」。
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
         * 「账号 + 密码」登录方式：账号即 {@link User#account}，密码即 {@link User#passwordHash}。
         * <p>该方式不会单独创建新 user —— 任何一种渠道注册的用户都会自动获得 account，
         * 只要他们后续设置过密码，即可走此入口登录。
         */
        ACCOUNT,
        /**
         * @deprecated 历史「内部测试账号」登录类型。线上已迁移到 ACCOUNT；
         * 仅为兼容历史数据库行的反序列化保留（{@code DataInitializer} 启动时会把 TEST 改写为 ACCOUNT）。
         * 后续维护中若确认数据库已无 TEST 行，可安全删除该枚举值。
         */
        @Deprecated
        TEST
    }

    /**
     * 注册来源枚举 —— 表示「该 user 首次被创建时的渠道」，一旦写入永不变更。
     */
    public enum RegistrationSource {
        WECHAT,
        PHONE,
        EMAIL,
        GOOGLE,
        APPLE,
        GUEST,
        /** admin 后台手动创建（运营 / 客服内部账号、QA 测试账号等） */
        ADMIN_CREATED,
        /** 历史数据 —— DataInitializer 启动时为缺失字段的旧 user 统一补此值 */
        LEGACY
    }
}
