package com.lanprojects.fitcoach.feedback.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
 *   <li>uid 加索引 — 后续做"我的反馈"查询能走 index。</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_feedback", indexes = {
        @Index(name = "idx_uid", columnList = "uid"),
        @Index(name = "idx_created_at", columnList = "created_at")
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
}
