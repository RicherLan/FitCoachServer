package com.lanprojects.fitcoach.admin.audit;

/**
 * 后台高危操作枚举 —— 与 {@link AdminAuditLog#action} 配套。
 *
 * <p>命名规范：动词_对象（全大写下划线分隔）。
 * 新增时务必保持一段时间内的稳定，因为已落库的旧记录不会被自动重写。
 *
 * <p>分类划分仅作 IDE 阅读用，DB 里只存 name 字符串：
 * <ul>
 *   <li>支付 / 退款：REFUND_ORDER</li>
 *   <li>会员：GRANT_MEMBERSHIP / REVOKE_MEMBERSHIP</li>
 *   <li>用户：BAN_USER / UNBAN_USER</li>
 *   <li>系统配置：UPDATE_SYS_CONFIG / REFRESH_SYS_CONFIG_CACHE</li>
 *   <li>套餐 / 训练 / 肌群 / 版本 / 反馈 / 日志任务 CRUD</li>
 *   <li>账户：LOGIN_SUCCESS / LOGIN_FAILED / CHANGE_PASSWORD</li>
 * </ul>
 */
public enum AdminAuditAction {

    // ===== 支付 / 退款 =====
    REFUND_ORDER,

    // ===== 会员 =====
    GRANT_MEMBERSHIP,
    REVOKE_MEMBERSHIP,

    // ===== 用户 =====
    BAN_USER,
    UNBAN_USER,

    // ===== 系统配置 =====
    UPDATE_SYS_CONFIG,
    REFRESH_SYS_CONFIG_CACHE,

    // ===== 套餐 =====
    CREATE_MEMBERSHIP_PLAN,
    UPDATE_MEMBERSHIP_PLAN,
    DELETE_MEMBERSHIP_PLAN,

    // ===== 训练 / 肌群 =====
    CREATE_EXERCISE,
    UPDATE_EXERCISE,
    DELETE_EXERCISE,
    CREATE_MUSCLE_GROUP,
    UPDATE_MUSCLE_GROUP,
    DELETE_MUSCLE_GROUP,

    // ===== App 版本 =====
    CREATE_APP_VERSION,
    UPDATE_APP_VERSION,
    DELETE_APP_VERSION,

    // ===== 反馈 =====
    UPDATE_FEEDBACK_STATUS,
    BATCH_UPDATE_FEEDBACK_STATUS,

    // ===== 日志远程拉取 =====
    CREATE_LOG_TASK,
    DELETE_LOG_TASK,
    BATCH_DELETE_LOG_TASK,

    // ===== 账户 =====
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    CHANGE_PASSWORD,
}
