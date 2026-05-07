package com.lanprojects.fitcoach.log.entity;

/**
 * 日志拉取任务状态机。
 *
 * <pre>
 *   PENDING (admin 创建后默认)
 *     │  客户端 pull 命中 → 同事务原子改为 UPLOADING（DB 行锁防并发多设备）
 *     ▼
 *   UPLOADING (assignedAt 写入；客户端开始打包/上传)
 *     │  上传成功 → UPLOADED
 *     │  上传失败 + retryCount<上限 → 回滚 PENDING
 *     │  上传失败 + retryCount=上限 → FAILED
 *     │  超过 5 分钟未完成 → scheduler 回滚 PENDING（retryCount-1 不变；新一轮 pull 会再领）
 *     ▼
 *   UPLOADED (终态，文件落盘；7 天后 scheduler 清盘 + 标 EXPIRED)
 *
 *   FAILED (终态，重试上限耗尽)
 *   EXPIRED (终态，PENDING 24h 未被领走 / UPLOADED 文件已被清理)
 * </pre>
 *
 * 用 {@code @Enumerated(EnumType.STRING)} 入库，便于运维直读。
 */
public enum LogPullStatus {
    /** 待客户端拉取 */
    PENDING,
    /** 客户端已领取，正在打包/上传 */
    UPLOADING,
    /** 上传完成，文件可下载 */
    UPLOADED,
    /** 失败（重试上限耗尽） */
    FAILED,
    /** 已过期（PENDING 24h 未领 / UPLOADED 7 天文件清理） */
    EXPIRED
}
