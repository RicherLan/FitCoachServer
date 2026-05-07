package com.lanprojects.fitcoach.login.sms;

/**
 * 短信下发服务抽象层
 * <p>
 * 切换 provider 不需要改业务代码：
 * <ul>
 *   <li>{@code sms.provider=mock}（默认）→ {@link MockSmsService}：仅打日志，开发期省钱</li>
 *   <li>{@code sms.provider=tencent} → {@link TencentSmsService}：真发短信</li>
 * </ul>
 * 后续接 阿里云 / 网易云信 / 七牛 直接加新实现 + 改一个 yml。
 */
public interface SmsService {

    /**
     * 发送验证码到指定手机号。失败抛 {@link SmsException}。
     *
     * @param phone     纯数字手机号（11 位国内号；OtpService 已校验过）
     * @param code      6 位验证码（OtpService 生成，绝不能落到本类的日志里）
     * @param ttlMinutes 有效期分钟数（用于短信模板占位 {2}）
     */
    void sendOtp(String phone, String code, int ttlMinutes);
}
