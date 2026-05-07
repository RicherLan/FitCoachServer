package com.lanprojects.fitcoach.log.dto;

import lombok.Data;

/**
 * 客户端 POST /api/logs/tasks/{id}/fail 的请求体。
 *
 * <p>客户端打包/上传过程中遇到不可恢复错误（zip 失败 / 文件不存在 等）时主动回报，
 * 服务端按重试上限决定回滚 PENDING 还是直接标 FAILED。
 */
@Data
public class ReportFailureRequest {
    /** 失败原因（最多 256 字符；超出截断） */
    private String reason;
}
