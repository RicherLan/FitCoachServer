package com.lanprojects.fitcoach.common.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一业务状态码
 *
 * <p><b>i18n 设计</b>：每个枚举值同时持有：
 * <ul>
 *   <li>{@code message}  — zh-CN 内置文案，作为 {@code messages_*.properties} 全部漏配时的最终兜底；</li>
 *   <li>{@code i18nKey}  — 国际化资源 key，对应
 *       {@code fitcoach-common/src/main/resources/i18n/messages_{lang}.properties} 里的同名条目。</li>
 * </ul>
 * 实际下发给客户端的 message 由 {@link com.lanprojects.fitcoach.common.i18n.I18nMessages#translate(ResultCode)}
 * 按 {@link com.lanprojects.fitcoach.common.client.ClientContext#locale()} 翻译得到。
 *
 * <p><b>命名规范</b>：i18nKey 全部小写蛇形 + 模块前缀（如 {@code auth.unauthorized}），
 * 与本枚举名称一一对应，便于双向追溯。新增错误码时务必同步加 7 种语言资源。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ====== 通用 ======
    SUCCESS(0, "success", "common.success"),
    ERROR(500, "服务器内部错误", "error.internal"),
    BAD_REQUEST(400, "请求参数错误", "error.bad_request"),

    // ====== 认证相关 1xxx ======
    UNAUTHORIZED(1001, "未登录或登录已过期", "auth.unauthorized"),
    TOKEN_EXPIRED(1002, "登录已过期，请重新登录", "auth.token_expired"),
    TOKEN_INVALID(1003, "无效的登录凭证", "auth.token_invalid"),
    REFRESH_TOKEN_INVALID(1004, "刷新凭证无效或已过期，请重新登录", "auth.refresh_token_invalid"),
    JWT_SECRET_MISSING(1005, "JWT 密钥未配置，请联系管理员", "auth.jwt_secret_missing"),
    /**
     * 单设备登录互踢：当前会话已被同一账号在另一设备登录的请求挤下线。
     * <p>触发链路：客户端 token 中的 sid 与 server 端 user.currentSessionId 不一致 →
     * {@link com.lanprojects.fitcoach.login.service.AuthService#getCurrentUser(String)} 抛此码 →
     * RN httpClient 全局拦截 → Toast"账号已在其他设备登录" + 强制 logout 跳登录页。
     * <p>仅当请求带有真实 deviceId（{@code ClientContext.get().hasDeviceId() == true}）的客户端登录时才会写入
     * user.currentSessionId，因此 admin 后台 / Postman 等无 deviceId 的调试场景不会被互踢。
     */
    SESSION_KICKED(1006, "账号已在其他设备登录", "auth.session_kicked"),

    // ====== 微信登录 2xxx ======
    WECHAT_CODE_INVALID(2001, "微信授权码无效", "wechat.code_invalid"),
    WECHAT_API_ERROR(2002, "微信接口调用失败", "wechat.api_error"),
    WECHAT_CONFIG_MISSING(2003, "微信配置缺失，请联系管理员", "wechat.config_missing"),

    // ====== 用户相关 3xxx ======
    USER_NOT_FOUND(3001, "用户不存在", "user.not_found"),
    USER_DISABLED(3002, "账号已被禁用", "user.disabled"),

    // ====== 手机号 / SMS 4xxx ======
    PHONE_INVALID(4001, "手机号格式不正确", "otp.phone_invalid"),
    OTP_SEND_TOO_FAST(4002, "验证码发送过于频繁，请稍后再试", "otp.send_too_fast"),
    OTP_SEND_LIMIT_EXCEEDED(4003, "今日验证码发送次数已达上限", "otp.send_limit_exceeded"),
    OTP_INVALID(4004, "验证码错误或已过期", "otp.invalid"),
    OTP_VERIFY_LIMIT_EXCEEDED(4005, "验证码错误次数过多，请重新获取", "otp.verify_limit_exceeded"),
    SMS_PROVIDER_ERROR(4006, "短信服务暂不可用，请稍后重试", "otp.sms_provider_error"),
    CAPTCHA_VERIFY_FAILED(4007, "人机验证未通过，请重试", "captcha.verify_failed"),
    CAPTCHA_SERVICE_ERROR(4008, "人机验证服务暂不可用，请稍后重试", "captcha.service_error"),

    // ====== 用户资料 / 文件上传 5xxx ======
    NICKNAME_INVALID(5001, "昵称长度需在 2-20 之间，且不能仅含空白字符", "profile.nickname_invalid"),
    GENDER_INVALID(5002, "性别取值不合法（0=未知, 1=男, 2=女）", "profile.gender_invalid"),
    PROFILE_NO_CHANGES(5003, "未提供任何要更新的字段", "profile.no_changes"),
    AVATAR_FILE_EMPTY(5101, "头像文件为空", "avatar.file_empty"),
    AVATAR_FILE_TOO_LARGE(5102, "头像文件过大，请重新选择", "avatar.file_too_large"),
    AVATAR_CONTENT_TYPE_INVALID(5103, "仅支持 jpg / png / webp 格式的头像", "avatar.content_type_invalid"),
    AVATAR_STORAGE_ERROR(5104, "头像保存失败，请稍后重试", "avatar.storage_error"),
    UPLOAD_FILE_TOO_LARGE(5901, "上传文件过大，请压缩后重试", "upload.file_too_large"),

    // ====== 意见反馈 6xxx ======
    FEEDBACK_TYPE_INVALID(6001, "反馈类型不合法", "feedback.type_invalid"),
    FEEDBACK_CONTENT_EMPTY(6002, "反馈内容不能为空", "feedback.content_empty"),
    FEEDBACK_CONTENT_TOO_LONG(6003, "反馈内容超过最大长度限制", "feedback.content_too_long"),
    FEEDBACK_ATTACHMENT_TOO_MANY(6004, "附件数量超过限制", "feedback.attachment_too_many"),
    FEEDBACK_ATTACHMENT_URL_INVALID(6005, "存在非法的附件 URL", "feedback.attachment_url_invalid"),
    FEEDBACK_ATTACHMENT_FILE_EMPTY(6101, "附件文件为空", "feedback.attachment_file_empty"),
    FEEDBACK_ATTACHMENT_FILE_TOO_LARGE(6102, "附件文件过大，请重新选择", "feedback.attachment_file_too_large"),
    FEEDBACK_ATTACHMENT_CONTENT_TYPE_INVALID(6103, "附件仅支持 jpg / png / webp 格式", "feedback.attachment_content_type_invalid"),
    FEEDBACK_ATTACHMENT_STORAGE_ERROR(6104, "附件保存失败，请稍后重试", "feedback.attachment_storage_error"),

    // ====== 后台管理 7xxx（fitcoach-admin 模块） ======
    // admin 后台不做 i18n（内部维护人员均为中文），i18nKey 仍登记一份保持代码一致性，
    // 但 messages_*.properties 仅 zh-CN 翻译；其他语言文件不收录这些 key（fallback 到 zh-CN）
    ADMIN_UNAUTHORIZED(7001, "管理员未登录或登录已过期", "admin.unauthorized"),
    ADMIN_TOKEN_INVALID(7002, "无效的管理员凭证", "admin.token_invalid"),
    ADMIN_LOGIN_FAILED(7003, "账号或密码错误", "admin.login_failed"),
    ADMIN_ACCOUNT_DISABLED(7004, "管理员账号已被禁用", "admin.account_disabled"),
    ADMIN_ACCOUNT_NOT_FOUND(7005, "管理员账号不存在", "admin.account_not_found"),
    ADMIN_PASSWORD_INVALID(7006, "密码长度需在 6-32 之间", "admin.password_invalid"),
    ADMIN_OLD_PASSWORD_WRONG(7007, "原密码不正确", "admin.old_password_wrong"),
    ADMIN_PERMISSION_DENIED(7008, "权限不足", "admin.permission_denied"),
    /**
     * 管理员登录失败次数过多，触发本地限流（{@link com.lanprojects.fitcoach.common.security.LoginAttemptLimiter}）。
     * <p>当前默认策略：username 维度 5 次/10min、IP 维度 10 次/10min；超限后必须等窗口过期再尝试。
     */
    ADMIN_LOGIN_RATE_LIMITED(7009, "登录尝试次数过多，请稍后再试", "admin.login_rate_limited"),
    ADMIN_FEEDBACK_NOT_FOUND(7101, "反馈记录不存在", "admin.feedback_not_found"),
    ADMIN_FEEDBACK_STATUS_INVALID(7102, "反馈状态值不合法", "admin.feedback_status_invalid"),
    ADMIN_USER_TARGET_NOT_FOUND(7201, "目标用户不存在", "admin.user_target_not_found"),

    // ====== 日志拉取 7301-7399（fitcoach-log 模块） ======
    LOG_TASK_NOT_FOUND(7301, "日志任务不存在", "log.task_not_found"),
    LOG_TASK_STATUS_INVALID(7302, "日志任务状态值不合法", "log.task_status_invalid"),
    LOG_TASK_TARGET_USER_NOT_FOUND(7303, "目标用户不存在", "log.task_target_user_not_found"),
    LOG_TASK_DUPLICATE_PENDING(7304, "该用户在 24h 内已存在未完成的日志任务，请勿重复创建", "log.task_duplicate_pending"),
    LOG_TASK_NOT_DOWNLOADABLE(7305, "日志任务尚未上传完成，无法下载", "log.task_not_downloadable"),
    LOG_TASK_FILE_MISSING(7306, "日志文件已被清理或不存在", "log.task_file_missing"),
    LOG_TASK_RETRY_LIMIT_EXCEEDED(7307, "日志任务上传重试次数已达上限", "log.task_retry_limit_exceeded"),
    LOG_TASK_REASSIGN_DENIED(7308, "日志任务已被其他设备/进程领取，请稍后重试", "log.task_reassign_denied"),
    LOG_UPLOAD_FILE_EMPTY(7311, "上传的日志文件为空", "log.upload_file_empty"),
    LOG_UPLOAD_FILE_TOO_LARGE(7312, "上传的日志文件过大", "log.upload_file_too_large"),
    LOG_UPLOAD_CONTENT_TYPE_INVALID(7313, "日志文件仅支持 application/zip", "log.upload_content_type_invalid"),
    LOG_UPLOAD_TASK_OWNER_MISMATCH(7314, "日志任务归属用户不匹配，已拒绝上传", "log.upload_task_owner_mismatch"),
    LOG_UPLOAD_TASK_STATUS_NOT_UPLOADING(7315, "日志任务当前状态不允许上传", "log.upload_task_status_not_uploading"),
    LOG_UPLOAD_STORAGE_ERROR(7316, "日志文件保存失败，请稍后重试", "log.upload_storage_error"),
    LOG_TASK_EXPIRED(7321, "日志任务已过期", "log.task_expired"),
    LOG_DOWNLOAD_IO_ERROR(7322, "日志文件读取失败", "log.download_io_error"),

    // ====== 客户端密码登录 / 改密 7401-7499（fitcoach-login PasswordService） ======
    PASSWORD_LOGIN_FAILED(7401, "手机号或密码错误", "password.login_failed"),
    PASSWORD_FORMAT_INVALID(7402, "密码需 6-32 位且至少包含 1 个字母和 1 个数字", "password.format_invalid"),
    PASSWORD_OLD_WRONG(7403, "原密码不正确", "password.old_wrong"),
    PASSWORD_OTP_REQUIRED(7404, "首次设置密码需先验证短信验证码", "password.otp_required"),
    PASSWORD_VERIFY_REQUIRED(7405, "请提供原密码或短信验证码以完成验证", "password.verify_required"),
    PASSWORD_NOT_SET(7406, "尚未设置密码", "password.not_set"),
    PASSWORD_PHONE_REQUIRED(7407, "请先绑定手机号才能设置密码", "password.phone_required"),
    /**
     * 密码登录失败次数过多，触发本地限流（{@link com.lanprojects.fitcoach.common.security.LoginAttemptLimiter}）。
     * <p>当前默认策略：phone 维度 5 次/15min、IP 维度 20 次/15min；超限后必须等窗口过期再尝试。
     */
    PASSWORD_LOGIN_RATE_LIMITED(7408, "登录尝试次数过多，请稍后再试", "password.login_rate_limited"),

    // ====== 健身动作 7501-7599（fitcoach-exercise） ======
    EXERCISE_NOT_FOUND(7501, "动作不存在", "exercise.not_found"),
    EXERCISE_KEY_DUPLICATE(7502, "动作 key 已存在，请勿重复创建", "exercise.key_duplicate"),
    EXERCISE_DISABLED(7503, "该动作已下架", "exercise.disabled"),
    EXERCISE_LAST_FREE_IN_GROUP(7504, "该肌群至少需保留一个免费动作，无法将其下线/置为付费", "exercise.last_free_in_group"),

    // ====== 肌群 7601-7699（fitcoach-exercise · MuscleGroup） ======
    MUSCLE_GROUP_NOT_FOUND(7601, "肌群不存在", "musclegroup.not_found"),
    MUSCLE_GROUP_KEY_DUPLICATE(7602, "肌群 key 已存在，请勿重复创建", "musclegroup.key_duplicate"),
    MUSCLE_GROUP_HAS_EXERCISES(7603, "该肌群下还有动作，无法删除（请先把动作迁移到其他肌群）", "musclegroup.has_exercises"),

    // ====== App 版本管理 7701-7799（fitcoach-appversion） ======
    APP_VERSION_NOT_FOUND(7701, "版本记录不存在", "appversion.not_found"),
    APP_VERSION_PLATFORM_INVALID(7702, "平台标识不合法（仅支持 android / ios）", "appversion.platform_invalid"),
    APP_VERSION_DUPLICATE(7703, "该平台下已存在相同 versionCode 的版本记录", "appversion.duplicate"),
    APP_VERSION_VERSION_CODE_INVALID(7704, "versionCode 必须为正整数", "appversion.version_code_invalid"),
    APP_VERSION_VERSION_NAME_INVALID(7705, "versionName 不能为空", "appversion.version_name_invalid"),
    APP_VERSION_DOWNLOAD_URL_INVALID(7706, "下载链接不能为空", "appversion.download_url_invalid"),

    // ====== 系统配置 7801-7899（fitcoach-admin · SysConfig） ======
    SYS_CONFIG_NOT_FOUND(7801, "配置项不存在", "admin.config_not_found"),

    // ====== 会员 8001-8099（fitcoach-membership） ======
    MEMBERSHIP_REQUIRED(8001, "该功能需要会员才可使用，请先开通会员", "membership.required"),
    MEMBERSHIP_PLAN_NOT_FOUND(8002, "套餐不存在或已下架", "membership.plan_not_found"),
    MEMBERSHIP_PLAN_DISABLED(8003, "该套餐已停售", "membership.plan_disabled"),
    MEMBERSHIP_PLAN_DURATION_INVALID(8004, "套餐有效期天数必须大于 0", "membership.plan_duration_invalid"),
    MEMBERSHIP_PLAN_PRICE_INVALID(8005, "套餐价格必须大于 0", "membership.plan_price_invalid"),
    MEMBERSHIP_PLAN_CODE_DUPLICATE(8006, "套餐 code 已存在，请勿重复创建", "membership.plan_code_duplicate"),
    MEMBERSHIP_PLAN_DELETE_DENIED(8007, "该套餐已有用户购买，无法删除（可下架）", "membership.plan_delete_denied"),
    MEMBERSHIP_NOT_ACTIVE(8008, "尚未开通会员或会员已过期", "membership.not_active"),
    MEMBERSHIP_GRANT_DAYS_INVALID(8009, "赠送/延长的天数必须为正整数", "membership.grant_days_invalid"),
    MEMBERSHIP_PLAN_CODE_INVALID(8010, "套餐 code 不能为空", "membership.plan_code_invalid"),

    // ====== 支付 8101-8199（fitcoach-payment） ======
    PAYMENT_CHANNEL_NOT_AVAILABLE(8101, "当前平台不支持该支付通道", "payment.channel_not_available"),
    PAYMENT_CHANNEL_DISABLED(8102, "该支付通道已停用", "payment.channel_disabled"),
    PAYMENT_ORDER_NOT_FOUND(8103, "订单不存在", "payment.order_not_found"),
    PAYMENT_ORDER_NOT_OWNED(8104, "订单不属于当前用户", "payment.order_not_owned"),
    PAYMENT_ORDER_STATUS_INVALID(8105, "订单状态不允许此操作", "payment.order_status_invalid"),
    PAYMENT_ORDER_AMOUNT_MISMATCH(8106, "订单金额校验失败（疑似篡改）", "payment.order_amount_mismatch"),
    PAYMENT_CALLBACK_SIGN_INVALID(8107, "支付回调签名校验失败", "payment.callback_sign_invalid"),
    PAYMENT_CALLBACK_DUPLICATE(8108, "支付回调重复", "payment.callback_duplicate"),
    PAYMENT_PROVIDER_ERROR(8109, "支付通道服务异常，请稍后重试", "payment.provider_error"),
    PAYMENT_RECEIPT_INVALID(8110, "Apple 收据校验失败", "payment.receipt_invalid"),
    PAYMENT_RECEIPT_ALREADY_USED(8111, "该收据已被使用，请勿重复提交", "payment.receipt_already_used"),
    PAYMENT_CONFIG_MISSING(8112, "支付通道配置缺失，请联系管理员", "payment.config_missing"),
    PAYMENT_PLATFORM_REQUIRED(8113, "无法识别客户端平台，请检查请求 Header", "payment.platform_required");

    private final int code;
    /** zh-CN 内置文案，作为 i18n properties 全部漏配时的最终兜底；不建议直接下发，请走 I18nMessages 翻译 */
    private final String message;
    /** i18n 资源 key，对应 messages_*.properties 内的同名条目 */
    private final String i18nKey;
}
