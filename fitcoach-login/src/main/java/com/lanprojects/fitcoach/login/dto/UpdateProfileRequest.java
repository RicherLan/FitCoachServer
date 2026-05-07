package com.lanprojects.fitcoach.login.dto;

import lombok.Data;

/**
 * 更新用户资料请求 — 所有字段都是可选，仅传上来的字段会被更新（PATCH 语义）。
 * <p>
 * 不在 DTO 上做 size/notnull 等校验，因为：
 * <ul>
 *   <li>所有字段都允许 null（缺省即不改）；</li>
 *   <li>非空字段的格式校验放在 service 层统一处理，错误码可控。</li>
 * </ul>
 */
@Data
public class UpdateProfileRequest {

    /**
     * 新昵称；null=不修改，非 null 时长度需 2-20。
     */
    private String nickname;

    /**
     * 性别：0=未知, 1=男, 2=女；null=不修改。
     */
    private Integer gender;
}
