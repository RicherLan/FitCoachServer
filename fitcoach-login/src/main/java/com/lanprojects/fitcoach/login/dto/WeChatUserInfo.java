package com.lanprojects.fitcoach.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 微信用户信息接口响应
 * <p>
 * 对应微信接口：https://api.weixin.qq.com/sns/userinfo
 */
@Data
public class WeChatUserInfo {

    @JsonProperty("openid")
    private String openId;

    @JsonProperty("nickname")
    private String nickname;

    /**
     * 性别：1=男, 2=女, 0=未知
     */
    @JsonProperty("sex")
    private Integer sex;

    @JsonProperty("province")
    private String province;

    @JsonProperty("city")
    private String city;

    @JsonProperty("country")
    private String country;

    /**
     * 头像 URL（最后一个数值代表方形头像大小：0/46/64/96/132）
     */
    @JsonProperty("headimgurl")
    private String headImgUrl;

    @JsonProperty("unionid")
    private String unionId;

    @JsonProperty("errcode")
    private Integer errCode;

    @JsonProperty("errmsg")
    private String errMsg;

    public boolean isSuccess() {
        return errCode == null || errCode == 0;
    }
}
