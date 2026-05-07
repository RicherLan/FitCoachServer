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
    USER_DISABLED(3002, "账号已被禁用");

    private final int code;
    private final String message;
}
