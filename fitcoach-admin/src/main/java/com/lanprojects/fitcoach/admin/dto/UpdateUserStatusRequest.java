package com.lanprojects.fitcoach.admin.dto;

import lombok.Data;

/**
 * 启用 / 禁用用户的请求体
 */
@Data
public class UpdateUserStatusRequest {
    /** true=启用, false=禁用 */
    private Boolean enabled;
}
