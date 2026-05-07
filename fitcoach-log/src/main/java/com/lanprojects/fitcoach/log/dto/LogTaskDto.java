package com.lanprojects.fitcoach.log.dto;

import com.lanprojects.fitcoach.log.entity.LogPullStatus;
import com.lanprojects.fitcoach.log.entity.LogPullTask;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * admin 后台展示的任务摘要 / 详情通用 DTO。
 *
 * <p>统一一个 DTO：列表项与详情字段差异极小（仅 remark/failReason 的可见性），
 * 不再拆 Summary/Detail，前端按需展示即可。
 *
 * <p>{@code downloadUrl} 在状态非 UPLOADED 时返回 null，前端据此控制下载按钮是否可用。
 */
@Data
@AllArgsConstructor
public class LogTaskDto {

    private Long id;
    private String uid;
    private LogPullStatus status;
    private String createdBy;
    private String remark;
    private Integer recentHours;

    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime uploadedAt;
    private LocalDateTime expireAt;

    private Long fileSizeBytes;
    private String downloadUrl;
    private int retryCount;
    private String failReason;

    /**
     * 把实体映射为 DTO。
     *
     * @param task         任务实体
     * @param downloadUrl  仅在状态 == UPLOADED 时由调用方拼好（如 /api/admin/logs/tasks/{id}/download），
     *                     其他状态传 null。
     */
    public static LogTaskDto from(LogPullTask task, String downloadUrl) {
        return new LogTaskDto(
                task.getId(),
                task.getUid(),
                task.getStatus(),
                task.getCreatedBy(),
                task.getRemark(),
                task.getRecentHours(),
                task.getCreatedAt(),
                task.getAssignedAt(),
                task.getUploadedAt(),
                task.getExpireAt(),
                task.getFileSizeBytes(),
                task.getStatus() == LogPullStatus.UPLOADED ? downloadUrl : null,
                task.getRetryCount(),
                task.getFailReason()
        );
    }
}
