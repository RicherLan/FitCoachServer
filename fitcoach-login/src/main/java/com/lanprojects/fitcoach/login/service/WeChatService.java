package com.lanprojects.fitcoach.login.service;

import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.dto.WeChatTokenResponse;
import com.lanprojects.fitcoach.login.dto.WeChatUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 微信 API 调用服务
 * <p>
 * AppId 和 AppSecret 从数据库配置中读取（SysConfigService），不硬编码。
 * <p>
 * 配置键：
 * - wechat.app_id   → 微信开放平台 AppID
 * - wechat.app_secret → 微信开放平台 AppSecret
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatService {

    // ====== 数据库配置键 ======
    public static final String CONFIG_APP_ID = "wechat.app_id";
    public static final String CONFIG_APP_SECRET = "wechat.app_secret";

    // ====== 微信 API 地址 ======
    private static final String TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token"
            + "?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
    private static final String USER_INFO_URL = "https://api.weixin.qq.com/sns/userinfo"
            + "?access_token=%s&openid=%s&lang=zh_CN";

    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;

    /**
     * 用授权码换取 access_token + openid
     * <p>
     * 对应微信文档：通过 code 获取 access_token
     *
     * @param code 客户端传来的微信授权码
     * @return 微信 token 响应（含 access_token、openid、unionid）
     */
    public WeChatTokenResponse getAccessToken(String code) {
        String appId = getAppId();
        String appSecret = getAppSecret();

        String url = String.format(TOKEN_URL, appId, appSecret, code);
        log.info("请求微信 access_token, appId={}", appId);

        try {
            String response = HttpUtil.get(url, 5000);
            log.debug("微信 access_token 响应: {}", response);

            WeChatTokenResponse tokenResp = objectMapper.readValue(response, WeChatTokenResponse.class);
            if (!tokenResp.isSuccess()) {
                log.error("微信 access_token 获取失败: errCode={}, errMsg={}", tokenResp.getErrCode(), tokenResp.getErrMsg());
                throw new BusinessException(ResultCode.WECHAT_CODE_INVALID,
                        "微信授权失败: " + tokenResp.getErrMsg());
            }
            return tokenResp;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信 access_token 接口异常", e);
            throw new BusinessException(ResultCode.WECHAT_API_ERROR, "微信接口调用失败，请稍后重试");
        }
    }

    /**
     * 用 access_token 获取微信用户信息
     *
     * @param accessToken 微信 access_token
     * @param openId      用户的 openid
     * @return 微信用户信息（昵称、头像、性别等）
     */
    public WeChatUserInfo getUserInfo(String accessToken, String openId) {
        String url = String.format(USER_INFO_URL, accessToken, openId);
        log.info("请求微信用户信息, openId={}", openId);

        try {
            String response = HttpUtil.get(url, 5000);
            log.debug("微信用户信息响应: {}", response);

            WeChatUserInfo userInfo = objectMapper.readValue(response, WeChatUserInfo.class);
            if (!userInfo.isSuccess()) {
                log.error("微信用户信息获取失败: errCode={}, errMsg={}", userInfo.getErrCode(), userInfo.getErrMsg());
                throw new BusinessException(ResultCode.WECHAT_API_ERROR,
                        "获取微信用户信息失败: " + userInfo.getErrMsg());
            }
            return userInfo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信用户信息接口异常", e);
            throw new BusinessException(ResultCode.WECHAT_API_ERROR, "微信接口调用失败，请稍后重试");
        }
    }

    // ====== 配置读取 ======

    private String getAppId() {
        String appId = sysConfigService.getValue(CONFIG_APP_ID);
        if (appId == null || appId.isBlank()) {
            throw new BusinessException(ResultCode.WECHAT_CONFIG_MISSING, "微信 AppID 未配置");
        }
        return appId;
    }

    private String getAppSecret() {
        String appSecret = sysConfigService.getValue(CONFIG_APP_SECRET);
        if (appSecret == null || appSecret.isBlank()) {
            throw new BusinessException(ResultCode.WECHAT_CONFIG_MISSING, "微信 AppSecret 未配置");
        }
        return appSecret;
    }
}
