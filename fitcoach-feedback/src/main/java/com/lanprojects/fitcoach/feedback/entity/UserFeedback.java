package com.lanprojects.fitcoach.feedback.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户意见反馈实体。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>uid 不做外键 — User 表在 fitcoach-login 模块，跨模块外键耦合更重；
 *       业务上反馈不强依赖用户存在（即使用户被注销，反馈也应保留供运营查阅）；</li>
 *   <li>type 用 {@code @Enumerated(EnumType.STRING)} — 数据库可读、扩展枚举值无需迁移；</li>
 *   <li>attachment_urls 走 {@link JsonStringListConverter} — 单条最多 5 个 URL，TEXT 列够用；</li>
 *   <li>uid / created_at / status 加索引 — 后续做"我的反馈" / 后台分页 / 状态筛选都能走 index。</li>
 *   <li>处理相关字段（status / handlerAdminId / handlerReply / handledAt）由 fitcoach-admin
 *       后台管理模块更新，客户端不感知；status 的默认值 PENDING 由 service 层在创建时显式赋值，
 *       同时为兼容存量行 / DDL 自动加列场景，字段上加 {@link Column#nullable()}=true。</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_feedback", indexes = {
        @Index(name = "idx_uid", columnList = "uid"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_status", columnList = "status")
})
public class UserFeedback extends BaseEntity {

    /** 提交者 uid（来自 User.uid，非外键，避免跨模块强耦合） */
    @Column(name = "uid", nullable = false, length = 64)
    private String uid;

    /** 反馈类型：SUGGESTION / EXPERIENCE / OTHER */
    @Column(name = "type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private FeedbackType type;

    /** 反馈正文。长度上限由 service 层按 UploadProperties.feedback.maxContentLength 校验 */
    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 附件 URL 列表（已上传到 storage 的相对/完整 URL）。
     * <p>用 JSON 序列化进 TEXT 列，避免单开 attachment 表；查询/写入仍以 {@code List<String>} 操作。
     */
    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "attachment_urls", columnDefinition = "TEXT")
    private List<String> attachmentUrls = new ArrayList<>();

    /** 创建时附带的客户端版本，便于按版本统计问题分布（可选） */
    @Column(name = "app_version", length = 32)
    private String appVersion;

    /** 客户端平台（android / ios），可选 */
    @Column(name = "platform", length = 16)
    private String platform;

    // ====== 后台处理相关字段（fitcoach-admin 模块写入） ======

    /**
     * 处理状态（默认 PENDING）。
     * <p>nullable=true 是为了兼容 ddl-auto=update 加列时存量行 NULL；service 读取时统一兜底为 PENDING。
     */
    @Column(name = "status", length = 32)
    @Enumerated(EnumType.STRING)
    private FeedbackStatus status;

    /** 处理人（管理员账号 username 或 id），由 admin 模块在状态流转时写入 */
    @Column(name = "handler_admin", length = 64)
    private String handlerAdmin;

    /** 处理回复（管理员填写，可选；客户端"我的反馈"展示用） */
    @Lob
    @Column(name = "handler_reply", columnDefinition = "TEXT")
    private String handlerReply;

    /** 最近一次状态流转时间（每次 status 变更时更新） */
    @Column(name = "handled_at")
    private LocalDateTime handledAt;
}
