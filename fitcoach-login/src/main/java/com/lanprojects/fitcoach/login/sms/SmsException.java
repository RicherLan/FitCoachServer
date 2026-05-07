package com.lanprojects.fitcoach.login.sms;

/**
 * SmsService 内部异常 — 由 OtpService 捕获后包装成业务错误码 SMS_PROVIDER_ERROR
 */
public class SmsException extends RuntimeException {
    public SmsException(String message) {
        super(message);
    }

    public SmsException(String message, Throwable cause) {
        super(message, cause);
    }
}
