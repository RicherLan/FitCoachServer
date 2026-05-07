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
    AVATAR_STORAGE_ERROR(5104, "头像保存失败，请稍后重试");

    private final int code;
    private final String message;
}
