package com.lanprojects.fitcoach.log.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 日志拉取模块配置 — 对应 application.yml 的 {@code log-pull.*} 节点。
 *
 * <p>命名走 {@code log-pull} 而非 {@code log}，避开 Spring Boot 自带的 {@code logging.*}/{@code log.*} 命名冲突。
 *
 * <p>所有时间窗口默认值见字段注释；生产环境若调整，只改 yml 不改代码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "log-pull")
public class LogProperties {

    /** 日志 zip 在 upload.base-dir 下的子目录（最终路径：{base-dir}/{sub-dir}/{uid}/{filename}） */
    private String subDir = "logs";

    /** 单个 zip 上限（字节）；默认 50MB —— 客户端打包前需自行裁剪 */
    private long maxUploadSizeBytes = 50L * 1024L * 1024L;

    /** PENDING 任务硬过期时长（小时）；超过即标 EXPIRED */
    private int pendingExpireHours = 24;

    /** UPLOADING 状态超时回滚阈值（分钟）；超过未完成回滚 PENDING */
    private int uploadingTimeoutMinutes = 5;

    /** UPLOADED 文件保留天数；超过即清盘 + 任务标 EXPIRED */
    private int uploadedRetentionDays = 7;

    /** 单任务最大重试次数（含首次）；累计 retryCount 达到此值即标 FAILED */
    private int maxRetryCount = 3;

    /** scheduler 扫描间隔（秒）；同时覆盖三类批扫（超时回滚/过期/清盘） */
    private int schedulerIntervalSeconds = 60;

    /** 同一 uid 在 24h 内允许的最大未完成任务数（创建端去重阈值） */
    private int maxActiveTaskPerUidIn24h = 1;
}
