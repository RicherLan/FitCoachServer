package com.lanprojects.fitcoach.login.sms;

import com.lanprojects.fitcoach.common.util.LogUtils;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 腾讯云 SMS 实现
 * <p>
 * 启用方式：在 application-prod.yml 配置 {@code sms.provider=tencent}，并通过环境变量提供：
 * <ul>
 *   <li>{@code TENCENT_SECRET_ID}</li>
 *   <li>{@code TENCENT_SECRET_KEY}</li>
 *   <li>{@code TENCENT_SMS_APP_ID}</li>
 *   <li>{@code TENCENT_SMS_TEMPLATE_ID}</li>
 *   <li>{@code TENCENT_SMS_SIGN_NAME}（如 "FitCoach"）</li>
 * </ul>
 * <p><b>安全</b>：本类绝不打印 OTP 明文，只打脱敏的手机号和 SDK 返回的 SerialNo。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sms.provider", havingValue = "tencent")
public class TencentSmsService implements SmsService {

    @Value("${sms.tencent.secret-id:}")
    private String secretId;

    @Value("${sms.tencent.secret-key:}")
    private String secretKey;

    @Value("${sms.tencent.sdk-app-id:}")
    private String sdkAppId;

    @Value("${sms.tencent.template-id:}")
    private String templateId;

    @Value("${sms.tencent.sign-name:}")
    private String signName;

    @Value("${sms.tencent.region:ap-guangzhou}")
    private String region;

    @Value("${sms.tencent.endpoint:sms.tencentcloudapi.com}")
    private String endpoint;

    private SmsClient smsClient;

    @PostConstruct
    public void init() {
        if (secretId.isBlank() || secretKey.isBlank() || sdkAppId.isBlank()
                || templateId.isBlank() || signName.isBlank()) {
            log.warn("腾讯云 SMS 配置不完整，sendOtp 调用将抛 SmsException。" +
                    "请检查 TENCENT_SECRET_ID/SECRET_KEY/SMS_APP_ID/SMS_TEMPLATE_ID/SMS_SIGN_NAME 环境变量");
            return;
        }
        Credential cred = new Credential(secretId, secretKey);
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(endpoint);
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        this.smsClient = new SmsClient(cred, region, clientProfile);
        log.info("TencentSmsService 已初始化, region={}, sdkAppId={}, templateId={}, signName={}",
                region, sdkAppId, templateId, signName);
    }

    @Override
    public void sendOtp(String phone, String code, int ttlMinutes) {
        if (smsClient == null) {
            throw new SmsException("腾讯云 SMS 未初始化（配置缺失）");
        }
        SendSmsRequest req = new SendSmsRequest();
        req.setSmsSdkAppId(sdkAppId);
        req.setSignName(signName);
        req.setTemplateId(templateId);
        // 模板假定为 "您的验证码是{1}，{2}分钟内有效..."；按你申请的模板调整顺序
        req.setTemplateParamSet(new String[]{code, String.valueOf(ttlMinutes)});
        // 国内号统一加 +86 前缀
        req.setPhoneNumberSet(new String[]{toE164(phone)});

        try {
            SendSmsResponse resp = smsClient.SendSms(req);
            SendStatus[] statuses = resp.getSendStatusSet();
            if (statuses == null || statuses.length == 0) {
                throw new SmsException("腾讯云 SMS 响应为空");
            }
            for (SendStatus status : statuses) {
                if (!"Ok".equalsIgnoreCase(status.getCode())) {
                    log.warn("腾讯云 SMS 发送失败, phone={}, code={}, msg={}, serial={}",
                            LogUtils.mask(phone), status.getCode(), status.getMessage(),
                            status.getSerialNo());
                    throw new SmsException("腾讯云 SMS 失败: " + status.getCode());
                }
            }
            log.info("腾讯云 SMS 已发送, phone={}, serial={}",
                    LogUtils.mask(phone), statuses[0].getSerialNo());
        } catch (TencentCloudSDKException e) {
            log.error("腾讯云 SMS SDK 异常, phone={}, errCode={}",
                    LogUtils.mask(phone), e.getErrorCode());
            throw new SmsException("腾讯云 SMS SDK 异常: " + e.getErrorCode(), e);
        }
    }

    /**
     * 把 11 位国内手机号转成 E.164 格式（+86xxxxxxxxxxx）。
     * 已经带 + 的视为海外号原样返回。
     */
    private String toE164(String phone) {
        if (phone.startsWith("+")) return phone;
        return "+86" + phone;
    }
}
