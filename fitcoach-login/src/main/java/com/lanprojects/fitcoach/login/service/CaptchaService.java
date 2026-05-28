package com.lanprojects.fitcoach.login.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 腾讯云行为验证码（CAPTCHA）校验服务
 * <p>
 * 前端通过 WebView 加载腾讯验证码 JS SDK，用户完成拖动/点选后得到 ticket + randstr，
 * 随请求一并提交到 server；本服务调用腾讯 CAPTCHA 后端校验接口验证票据合法性。
 * <p>
 * 腾讯验证码后端校验文档：
 * <a href="https://cloud.tencent.com/document/product/1110/36926">DescribeCaptchaResult</a>
 * <p>
 * 本服务使用 hutool-http 直接调用 REST API（轻量，无需引入腾讯 CAPTCHA 专用 SDK），
 * 与项目中调用微信 API 的方式保持一致。
 * <p>
 * <b>配置来源</b>：所有配置（enabled / appId / appSecretKey）均从 {@link SysConfigService} 读取
 * （即 sys_config 数据库表 + 内存缓存），管理员可在后台管理平台动态修改，无需重启服务。
 *
 * @see com.lanprojects.fitcoach.login.controller.AuthController#sendPhoneCode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    /** 腾讯验证码后端校验 API 地址 */
    private static final String CAPTCHA_VERIFY_URL = "https://ssl.captcha.qq.com/ticket/verify";

    /** sys_config 表中的 key（与 CaptchaConfigSeeder 常量一致） */
    private static final String KEY_CAPTCHA_ENABLED = "captcha.enabled";
    private static final String KEY_CAPTCHA_APP_ID = "captcha.app_id";
    private static final String KEY_CAPTCHA_APP_SECRET_KEY = "captcha.app_secret_key";

    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 校验腾讯验证码 ticket。
     * <p>
     * 当 captcha.enabled=false 时（本地开发），跳过校验直接放行。
     *
     * @param ticket   前端验证成功后返回的票据
     * @param randstr  前端验证成功后返回的随机字符串
     * @param clientIp 客户端真实 IP（腾讯要求传入，用于风控判断）
     * @throws BusinessException CAPTCHA_VERIFY_FAILED 当校验失败时
     */
    public void verify(String ticket, String randstr, String clientIp) {
        boolean enabled = sysConfigService.getBoolValue(KEY_CAPTCHA_ENABLED, false);
        if (!enabled) {
            log.info("[CaptchaService] 验证码校验已关闭（captcha.enabled=false），跳过");
            return;
        }

        if (ticket == null || ticket.isBlank() || randstr == null || randstr.isBlank()) {
            log.warn("[CaptchaService] ticket 或 randstr 为空，拒绝请求");
            throw new BusinessException(ResultCode.CAPTCHA_VERIFY_FAILED);
        }

        String captchaAppId = sysConfigService.getValue(KEY_CAPTCHA_APP_ID);
        String appSecretKey = sysConfigService.getValue(KEY_CAPTCHA_APP_SECRET_KEY);

        if (captchaAppId == null || captchaAppId.isBlank() || appSecretKey == null || appSecretKey.isBlank()) {
            log.error("[CaptchaService] 验证码配置缺失（captcha.app_id / captcha.app_secret_key），请在后台管理中配置");
            throw new BusinessException(ResultCode.CAPTCHA_SERVICE_ERROR);
        }

        try {
            HttpResponse response = HttpRequest.get(CAPTCHA_VERIFY_URL)
                    .form("aid", captchaAppId)
                    .form("AppSecretKey", appSecretKey)
                    .form("Ticket", ticket)
                    .form("Randstr", randstr)
                    .form("UserIP", clientIp)
                    .timeout(5000)
                    .execute();

            String body = response.body();
            log.debug("[CaptchaService] 腾讯验证码校验响应: {}", body);

            JsonNode root = objectMapper.readTree(body);
            int responseCode = root.path("response").asInt(-1);

            // response=1 表示验证通过
            if (responseCode != 1) {
                int errCode = root.path("err_msg").asInt(0);
                String errMsg = root.path("err_msg").asText("unknown");
                log.warn("[CaptchaService] 验证码校验失败: response={}, err_msg={}", responseCode, errMsg);
                throw new BusinessException(ResultCode.CAPTCHA_VERIFY_FAILED);
            }

            log.info("[CaptchaService] 验证码校验通过");
        } catch (BusinessException e) {
            throw e; // 重新抛出业务异常
        } catch (Exception e) {
            log.error("[CaptchaService] 调用腾讯验证码校验接口异常", e);
            throw new BusinessException(ResultCode.CAPTCHA_SERVICE_ERROR);
        }
    }
}
