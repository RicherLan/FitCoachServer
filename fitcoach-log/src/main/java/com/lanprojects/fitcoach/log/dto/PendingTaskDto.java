package com.lanprojects.fitcoach.log.dto;

import com.lanprojects.fitcoach.log.entity.LogPullTask;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 客户端通用轮询入口 {@code GET /api/client/poll} 中 {@code data.logTask} 字段的内容。
 *
 * <p>命中：返回 {@code taskId / recentHours / expireAtMillis / uploadingDeadlineMillis}；
 * 未命中：响应 data 中不出现 {@code logTask} 字段（contribution 返回 null 时被 controller 忽略），
 * 客户端按 120s 周期下次再来。
 */
@Data
@AllArgsConstructor
public class PendingTaskDto {

    /** 服务端任务 id，客户端必须原样回传给 upload 接口 */
    private Long taskId;

    /** 客户端打包时按 createdAt >= now - recentHours 过滤；null 表示全量（受客户端兜底约束） */
    private Integer recentHours;

    /** 任务硬过期时间（毫秒）；客户端打包+上传必须在此之前完成，否则上传会被服务端拒（已 EXPIRED） */
    private long expireAtMillis;

    /** UPLOADING 超时回滚阈值（毫秒）；客户端可据此判断 "我应该在这之前上传完" */
    private long uploadingDeadlineMillis;

    public static PendingTaskDto from(LogPullTask task, long uploadingDeadlineMillis) {
        return new PendingTaskDto(
                task.getId(),
                task.getRecentHours(),
                task.getExpireAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                uploadingDeadlineMillis
        );
    }
}
