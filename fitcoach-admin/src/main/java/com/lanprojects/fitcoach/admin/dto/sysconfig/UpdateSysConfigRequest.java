package com.lanprojects.fitcoach.admin.dto.sysconfig;

import lombok.Getter;
import lombok.Setter;

/**
 * 更新系统配置请求体。
 * <p>
 * 所有字段可选（null = 不修改）：
 * <ul>
 *   <li>{@code configValue} — 对于加密字段，传入明文，server 自动加密入库；</li>
 *   <li>{@code description} — 配置描述信息；</li>
 *   <li>{@code enabled} — 启用/禁用开关。</li>
 * </ul>
 */
@Getter
@Setter
public class UpdateSysConfigRequest {

    private String configValue;
    private String description;
    private Boolean enabled;
}
