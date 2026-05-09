package com.lanprojects.fitcoach.common.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一业务状态码
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ====== 通用 ======
    SUCCESS(0, "success"),
    ERROR(500, "服务器内部错误"),
    BAD_REQUEST(400, "请求参数错误"),

    // ====== 认证相关 1xxx ======
    UNAUTHORIZED(1001, "未登录或登录已过期"),
    TOKEN_EXPIRED(1002, "登录已过期，请重新登录"),
    TOKEN_INVALID(1003, "无效的登录凭证"),
    REFRESH_TOKEN_INVALID(1004, "刷新凭证无效或已过期，请重新登录"),
    JWT_SECRET_MISSING(1005, "JWT 密钥未配置，请联系管理员"),
    /**
     * 单设备登录互踢：当前会话已被同一账号在另一设备登录的请求挤下线。
     * <p>触发链路：客户端 token 中的 sid 与 server 端 user.currentSessionId 不一致 →
     * {@link com.lanprojects.fitcoach.login.service.AuthService#getCurrentUser(String)} 抛此码 →
     * RN httpClient 全局拦截 → Toast"账号已在其他设备登录" + 强制 logout 跳登录页。
     * <p>仅当请求带有真实 deviceId（{@code ClientContext.get().hasDeviceId() == true}）的客户端登录时才会写入
     * user.currentSessionId，因此 admin 后台 / Postman 等无 deviceId 的调试场景不会被互踢。
     */
    SESSION_KICKED(1006, "账号已在其他设备登录"),

    // ====== 微信登录 2xxx ======
    WECHAT_CODE_INVALID(2001, "微信授权码无效"),
    WECHAT_API_ERROR(2002, "微信接口调用失败"),
    WECHAT_CONFIG_MISSING(2003, "微信配置缺失，请联系管理员"),

    // ====== 用户相关 3xxx ======
    USER_NOT_FOUND(3001, "用户不存在"),
    USER_DISABLED(3002, "账号已被禁用"),

    // ====== 手机号 / SMS 4xxx ======
    PHONE_INVALID(4001, "手机号格式不正确"),
    OTP_SEND_TOO_FAST(4002, "验证码发送过于频繁，请稍后再试"),
    OTP_SEND_LIMIT_EXCEEDED(4003, "今日验证码发送次数已达上限"),
    OTP_INVALID(4004, "验证码错误或已过期"),
    OTP_VERIFY_LIMIT_EXCEEDED(4005, "验证码错误次数过多，请重新获取"),
    SMS_PROVIDER_ERROR(4006, "短信服务暂不可用，请稍后重试"),

    // ====== 用户资料 / 文件上传 5xxx ======
    NICKNAME_INVALID(5001, "昵称长度需在 2-20 之间，且不能仅含空白字符"),
    GENDER_INVALID(5002, "性别取值不合法（0=未知, 1=男, 2=女）"),
    PROFILE_NO_CHANGES(5003, "未提供任何要更新的字段"),
    AVATAR_FILE_EMPTY(5101, "头像文件为空"),
    AVATAR_FILE_TOO_LARGE(5102, "头像文件过大，请重新选择"),
    AVATAR_CONTENT_TYPE_INVALID(5103, "仅支持 jpg / png / webp 格式的头像"),
    AVATAR_STORAGE_ERROR(5104, "头像保存失败，请稍后重试"),
    UPLOAD_FILE_TOO_LARGE(5901, "上传文件过大，请压缩后重试"),

    // ====== 意见反馈 6xxx ======
    FEEDBACK_TYPE_INVALID(6001, "反馈类型不合法"),
    FEEDBACK_CONTENT_EMPTY(6002, "反馈内容不能为空"),
    FEEDBACK_CONTENT_TOO_LONG(6003, "反馈内容超过最大长度限制"),
    FEEDBACK_ATTACHMENT_TOO_MANY(6004, "附件数量超过限制"),
    FEEDBACK_ATTACHMENT_URL_INVALID(6005, "存在非法的附件 URL"),
    FEEDBACK_ATTACHMENT_FILE_EMPTY(6101, "附件文件为空"),
    FEEDBACK_ATTACHMENT_FILE_TOO_LARGE(6102, "附件文件过大，请重新选择"),
    FEEDBACK_ATTACHMENT_CONTENT_TYPE_INVALID(6103, "附件仅支持 jpg / png / webp 格式"),
    FEEDBACK_ATTACHMENT_STORAGE_ERROR(6104, "附件保存失败，请稍后重试"),

    // ====== 后台管理 7xxx（fitcoach-admin 模块） ======
    ADMIN_UNAUTHORIZED(7001, "管理员未登录或登录已过期"),
    ADMIN_TOKEN_INVALID(7002, "无效的管理员凭证"),
    ADMIN_LOGIN_FAILED(7003, "账号或密码错误"),
    ADMIN_ACCOUNT_DISABLED(7004, "管理员账号已被禁用"),
    ADMIN_ACCOUNT_NOT_FOUND(7005, "管理员账号不存在"),
    ADMIN_PASSWORD_INVALID(7006, "密码长度需在 6-32 之间"),
    ADMIN_OLD_PASSWORD_WRONG(7007, "原密码不正确"),
    ADMIN_PERMISSION_DENIED(7008, "权限不足"),
    ADMIN_FEEDBACK_NOT_FOUND(7101, "反馈记录不存在"),
    ADMIN_FEEDBACK_STATUS_INVALID(7102, "反馈状态值不合法"),
    ADMIN_USER_TARGET_NOT_FOUND(7201, "目标用户不存在"),

    // ====== 日志拉取 7301-7399（fitcoach-log 模块） ======
    LOG_TASK_NOT_FOUND(7301, "日志任务不存在"),
    LOG_TASK_STATUS_INVALID(7302, "日志任务状态值不合法"),
    LOG_TASK_TARGET_USER_NOT_FOUND(7303, "目标用户不存在"),
    LOG_TASK_DUPLICATE_PENDING(7304, "该用户在 24h 内已存在未完成的日志任务，请勿重复创建"),
    LOG_TASK_NOT_DOWNLOADABLE(7305, "日志任务尚未上传完成，无法下载"),
    LOG_TASK_FILE_MISSING(7306, "日志文件已被清理或不存在"),
    LOG_TASK_RETRY_LIMIT_EXCEEDED(7307, "日志任务上传重试次数已达上限"),
    LOG_TASK_REASSIGN_DENIED(7308, "日志任务已被其他设备/进程领取，请稍后重试"),
    LOG_UPLOAD_FILE_EMPTY(7311, "上传的日志文件为空"),
    LOG_UPLOAD_FILE_TOO_LARGE(7312, "上传的日志文件过大"),
    LOG_UPLOAD_CONTENT_TYPE_INVALID(7313, "日志文件仅支持 application/zip"),
    LOG_UPLOAD_TASK_OWNER_MISMATCH(7314, "日志任务归属用户不匹配，已拒绝上传"),
    LOG_UPLOAD_TASK_STATUS_NOT_UPLOADING(7315, "日志任务当前状态不允许上传"),
    LOG_UPLOAD_STORAGE_ERROR(7316, "日志文件保存失败，请稍后重试"),
    LOG_TASK_EXPIRED(7321, "日志任务已过期"),
    LOG_DOWNLOAD_IO_ERROR(7322, "日志文件读取失败"),

    // ====== 客户端密码登录 / 改密 7401-7499（fitcoach-login PasswordService） ======
    PASSWORD_LOGIN_FAILED(7401, "手机号或密码错误"),
    PASSWORD_FORMAT_INVALID(7402, "密码需 6-32 位且至少包含 1 个字母和 1 个数字"),
    PASSWORD_OLD_WRONG(7403, "原密码不正确"),
    PASSWORD_OTP_REQUIRED(7404, "首次设置密码需先验证短信验证码"),
    PASSWORD_VERIFY_REQUIRED(7405, "请提供原密码或短信验证码以完成验证"),
    PASSWORD_NOT_SET(7406, "尚未设置密码"),
    PASSWORD_PHONE_REQUIRED(7407, "请先绑定手机号才能设置密码"),

    // ====== 健身动作 7501-7599（fitcoach-exercise） ======
    EXERCISE_NOT_FOUND(7501, "动作不存在"),
    EXERCISE_KEY_DUPLICATE(7502, "动作 key 已存在，请勿重复创建"),
    EXERCISE_DISABLED(7503, "该动作已下架"),
    EXERCISE_LAST_FREE_IN_GROUP(7504, "该肌群至少需保留一个免费动作，无法将其下线/置为付费"),

    // ====== 肌群 7601-7699（fitcoach-exercise · MuscleGroup） ======
    MUSCLE_GROUP_NOT_FOUND(7601, "肌群不存在"),
    MUSCLE_GROUP_KEY_DUPLICATE(7602, "肌群 key 已存在，请勿重复创建"),
    MUSCLE_GROUP_HAS_EXERCISES(7603, "该肌群下还有动作，无法删除（请先把动作迁移到其他肌群）"),

    // ====== 会员 8001-8099（fitcoach-membership） ======
    MEMBERSHIP_REQUIRED(8001, "该功能需要会员才可使用，请先开通会员"),
    MEMBERSHIP_PLAN_NOT_FOUND(8002, "套餐不存在或已下架"),
    MEMBERSHIP_PLAN_DISABLED(8003, "该套餐已停售"),
    MEMBERSHIP_PLAN_DURATION_INVALID(8004, "套餐有效期天数必须大于 0"),
    MEMBERSHIP_PLAN_PRICE_INVALID(8005, "套餐价格必须大于 0"),
    MEMBERSHIP_PLAN_CODE_DUPLICATE(8006, "套餐 code 已存在，请勿重复创建"),
    MEMBERSHIP_PLAN_DELETE_DENIED(8007, "该套餐已有用户购买，无法删除（可下架）"),
    MEMBERSHIP_NOT_ACTIVE(8008, "尚未开通会员或会员已过期"),
    MEMBERSHIP_GRANT_DAYS_INVALID(8009, "赠送/延长的天数必须为正整数"),
    MEMBERSHIP_PLAN_CODE_INVALID(8010, "套餐 code 不能为空"),

    // ====== 支付 8101-8199（fitcoach-payment） ======
    PAYMENT_CHANNEL_NOT_AVAILABLE(8101, "当前平台不支持该支付通道"),
    PAYMENT_CHANNEL_DISABLED(8102, "该支付通道已停用"),
    PAYMENT_ORDER_NOT_FOUND(8103, "订单不存在"),
    PAYMENT_ORDER_NOT_OWNED(8104, "订单不属于当前用户"),
    PAYMENT_ORDER_STATUS_INVALID(8105, "订单状态不允许此操作"),
    PAYMENT_ORDER_AMOUNT_MISMATCH(8106, "订单金额校验失败（疑似篡改）"),
    PAYMENT_CALLBACK_SIGN_INVALID(8107, "支付回调签名校验失败"),
    PAYMENT_CALLBACK_DUPLICATE(8108, "支付回调重复"),
    PAYMENT_PROVIDER_ERROR(8109, "支付通道服务异常，请稍后重试"),
    PAYMENT_RECEIPT_INVALID(8110, "Apple 收据校验失败"),
    PAYMENT_RECEIPT_ALREADY_USED(8111, "该收据已被使用，请勿重复提交"),
    PAYMENT_CONFIG_MISSING(8112, "支付通道配置缺失，请联系管理员"),
    PAYMENT_PLATFORM_REQUIRED(8113, "无法识别客户端平台，请检查请求 Header");

    private final int code;
    private final String message;
}
