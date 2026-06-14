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
    /** admin 后台手动创建 C 端 user（{@code registrationSource=ADMIN_CREATED}） */
    CREATE_USER,
    /** admin 后台重置 C 端 user 的登录密码（{@code passwordHash} 被覆盖） */
    RESET_USER_PASSWORD,

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

    // ===== 训练记录 / 训练动作库 =====
    CREATE_TRAINING_EXERCISE,
    UPDATE_TRAINING_EXERCISE,
    DELETE_TRAINING_EXERCISE,
    /** admin 给训练动作上传 / 替换自定义图标（{@code training_exercise.icon_url}） */
    UPLOAD_TRAINING_EXERCISE_ICON,
    /** admin 删除训练动作的自定义图标，回退到 emoji 渲染 */
    DELETE_TRAINING_EXERCISE_ICON,

    // ===== App 版本 =====
    CREATE_APP_VERSION,
    UPDATE_APP_VERSION,
    DELETE_APP_VERSION,
    UPLOAD_APP_VERSION_PACKAGE,
    UPLOAD_APP_VERSION_MAPPING,
    DELETE_APP_VERSION_FILE,

    // ===== 系统通知（站内弹窗） =====
    CREATE_SYS_NOTIFICATION,
    UPDATE_SYS_NOTIFICATION,
    DELETE_SYS_NOTIFICATION,

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

    // ===== 内部测试账号（历史枚举，user.loginType=TEST，已下线） =====
    // 保留枚举值仅为兼容 admin_audit_log 表中历史行的反序列化；
    // 新代码已统一走 CREATE_USER / RESET_USER_PASSWORD（user.account 体系）。
    @Deprecated CREATE_TEST_ACCOUNT,
    @Deprecated UPDATE_TEST_ACCOUNT,
    @Deprecated DELETE_TEST_ACCOUNT,
    @Deprecated RESET_TEST_ACCOUNT_PASSWORD,

    // ===== 数据导出 =====
    EXPORT_USERS,
    EXPORT_ORDERS,
    EXPORT_FEEDBACKS,
    EXPORT_AUDIT_LOGS,
    EXPORT_TRAINING_RECORDS,
}
