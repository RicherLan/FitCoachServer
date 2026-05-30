package com.lanprojects.fitcoach.admin.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 后台管理员高危操作审计日志。
 *
 * <p>每条记录回答四个问题：<b>谁 / 什么时候 / 对什么 / 做了什么</b>。
 *
 * <p>设计说明：
 * <ul>
 *   <li>不继承 {@link com.lanprojects.fitcoach.common.entity.BaseEntity}，因为审计日志不需要
 *       {@code updated_at}（写入后不可变）；created_at 用 {@link CreationTimestamp} 单独维护；</li>
 *   <li>{@code action} 用枚举 STRING 存，方便后续按 action 维度做查询/聚合；</li>
 *   <li>{@code summary} 是人类可读的一行摘要，控制在 1KB 内；详细参数可后续接 JSON 列；</li>
 *   <li>{@code success=true} 时 {@code errorMsg} 留空；失败时 server 抛异常路径上层捕获后落一条 FAILED；</li>
 *   <li>不存请求 body / 响应 body —— 体积太大也容易混入敏感字段。
 *       敏感字段（密码、token）严禁出现在 summary。</li>
 * </ul>
 *
 * <p>查询入口：{@link AdminAuditLogController}（仅 SUPER_ADMIN 可访问）。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "admin_audit_log",
        indexes = {
                @Index(name = "idx_audit_username_time", columnList = "admin_username, created_at"),
                @Index(name = "idx_audit_action_time", columnList = "action, created_at"),
                @Index(name = "idx_audit_target", columnList = "target_type, target_id"),
                @Index(name = "idx_audit_created_at", columnList = "created_at")
        }
)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作管理员 username（admin_user.username 的弱引用，不做外键以免历史数据被联动删除） */
    @Column(name = "admin_username", nullable = false, length = 64)
    private String adminUsername;

    /** 操作时的角色快照（SUPER_ADMIN / ADMIN / VIEWER） */
    @Column(name = "admin_role", length = 32)
    private String adminRole;

    /** 操作类型（枚举名） */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 64)
    private AdminAuditAction action;

    /** 目标对象类型：ORDER / USER / MEMBERSHIP / SYS_CONFIG / PLAN / EXERCISE / FEEDBACK / SELF / NONE 等 */
    @Column(name = "target_type", length = 32)
    private String targetType;

    /** 目标对象主键（字符串化，例如 orderId / uid / planCode / configKey / id） */
    @Column(name = "target_id", length = 128)
    private String targetId;

    /**
     * 人类可读的一行摘要。
     * <p>例如："refund 9900 cents from PAID, reason: 用户主动申请"，
     * 或 "grant 30 days of planCode=PRO_MONTHLY, reason: 客户补偿"。
     * <p>不允许出现密码 / token / 完整手机号等敏感字段。
     */
    @Column(name = "summary", length = 1024)
    private String summary;

    /** 是否成功 */
    @Column(name = "success", nullable = false)
    private Boolean success = true;

    /** 失败时的错误信息（成功时留空） */
    @Column(name = "error_msg", length = 512)
    private String errorMsg;

    /** 客户端真实 IP（通过 ClientIpResolver 解析，可能为内网/null） */
    @Column(name = "ip", length = 64)
    private String ip;

    /** User-Agent 头（截断到 256 字符避免极端长度） */
    @Column(name = "ua", length = 256)
    private String ua;

    /** 请求路径 + Method（便于复现：POST /api/admin/payment/orders/xxx/refund） */
    @Column(name = "request_uri", length = 256)
    private String requestUri;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
