package com.lanprojects.fitcoach.admin.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 管理员账号实体。
 * <p>
 * 与客户端 {@code User} 表完全分离：
 * <ul>
 *   <li>客户端用户用 uid + 第三方授权 / 手机号；管理员用 username + BCrypt 密码；</li>
 *   <li>独立张表方便后续做权限矩阵 / 操作日志关联，也避免误把管理员账号当普通用户暴露给前端；</li>
 *   <li>password 列只存 BCrypt 哈希，不存明文；BCrypt 自带盐 + 自适应成本，不需要单独 salt 列。</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "admin_user", indexes = {
        @Index(name = "uk_admin_username", columnList = "username", unique = true)
})
public class AdminUser extends BaseEntity {

    /** 登录用户名（唯一） */
    @Column(name = "username", nullable = false, length = 64)
    private String username;

    /**
     * 密码哈希（BCrypt，60 字符固定长度）。
     * <p>仅 service 层在 setRawPassword / 校验时接触明文，DB 永远只存哈希。
     */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** 显示名 — 用于后台 Header 展示，没填时回退到 username */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /**
     * 角色：SUPER_ADMIN / ADMIN / VIEWER。
     * 用 {@code EnumType.STRING} — DB 可读，新增枚举值无需迁移。
     */
    @Column(name = "role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private AdminRole role = AdminRole.ADMIN;

    /** 账号状态：true=启用, false=禁用（禁用后 token 校验失败） */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /** 最后登录时间（每次 login 时更新，便于审计） */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
