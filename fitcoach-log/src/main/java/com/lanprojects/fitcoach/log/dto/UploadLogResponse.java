package com.lanprojects.fitcoach.log.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 客户端 POST /api/logs/upload 的返回。
 *
 * <p>idempotent=true 表示是幂等命中（任务已 UPLOADED，未实际写盘），客户端可清掉本地未确认队列。
 */
@Data
@AllArgsConstructor
public class UploadLogResponse {
    private Long taskId;
    private long fileSizeBytes;
    private boolean idempotent;
}
