package com.lanprojects.fitcoach.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 微信 access_token 接口响应
 * <p>
 * 对应微信接口：https://api.weixin.qq.com/sns/oauth2/access_token
 */
@Data
public class WeChatTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("openid")
    private String openId;

    @JsonProperty("unionid")
    private String unionId;

    @JsonProperty("scope")
    private String scope;

    /**
     * 错误码（调用失败时有值）
     */
    @JsonProperty("errcode")
    private Integer errCode;

    /**
     * 错误信息
     */
    @JsonProperty("errmsg")
    private String errMsg;

    /**
     * 是否调用成功
     */
    public boolean isSuccess() {
        return errCode == null || errCode == 0;
    }
}
