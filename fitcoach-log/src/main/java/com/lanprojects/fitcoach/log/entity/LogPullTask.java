package com.lanprojects.fitcoach.log.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 日志拉取任务实体。
 *
 * <p>设计要点：
 * <ul>
 *   <li>uid 不做外键 — 与 UserFeedback 同思路，跨模块外键耦合更重；用户被注销也保留任务记录便于追溯；</li>
 *   <li>uid + status 联合索引覆盖客户端通用轮询入口的 logTask 拉取（最高频 path，
 *       由 LogPullContribution 触发的 LogPullService.claimNextPending 走该索引）；</li>
 *   <li>status + assignedAt 联合索引给 scheduler 扫超时 UPLOADING 用；</li>
 *   <li>status + uploadedAt 联合索引给 scheduler 扫 7 天前 UPLOADED 清理用；</li>
 *   <li>retryCount + failReason 用于失败回溯；</li>
 *   <li>fileSize/relativePath/downloadUrl 在上传完成后回填，PENDING/UPLOADING 阶段为 NULL。</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "log_pull_task", indexes = {
        @Index(name = "idx_log_uid_status", columnList = "uid,status"),
        @Index(name = "idx_log_status_assigned", columnList = "status,assigned_at"),
        @Index(name = "idx_log_status_uploaded", columnList = "status,uploaded_at"),
        @Index(name = "idx_log_created_at", columnList = "created_at")
})
public class LogPullTask extends BaseEntity {

    /** 任务归属用户 uid（与 User.uid 一致，非外键） */
    @Column(name = "uid", nullable = false, length = 64)
    private String uid;

    /** 任务状态 */
    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private LogPullStatus status;

    /** 创建人（admin 后台 username）；服务端 scheduler 自动创建时为 "system" */
    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    /** 备注（admin 创建时填写，下载时给 admin 看；可选） */
    @Column(name = "remark", length = 256)
    private String remark;

    /** 客户端要拉取的日志最近时长（小时）；NULL 表示拉所有可用日志 */
    @Column(name = "recent_hours")
    private Integer recentHours;

    // ====== 状态流转时间戳 ======

    /** 状态首次进入 UPLOADING 的时间（用于 5 分钟超时回滚扫描） */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** 状态进入 UPLOADED 的时间（用于 7 天文件清理扫描） */
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    /** 任务硬过期时间（默认 createdAt + 24h）；超过后 scheduler 标 EXPIRED */
    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

    // ====== 上传产物 ======

    /** zip 文件相对路径（subDir + uid + 文件名），上传完成后回填 */
    @Column(name = "file_relative_path", length = 512)
    private String fileRelativePath;

    /** zip 文件大小（字节），上传完成后回填 */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    // ====== 失败与重试 ======

    /** 已失败重试次数；UPLOADING 上传失败时 +1，达到上限标 FAILED */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** 最近一次失败原因（客户端上传失败回报 / scheduler 超时回滚 / 重试耗尽）；最多 512 字符 */
    @Column(name = "fail_reason", length = 512)
    private String failReason;
}
