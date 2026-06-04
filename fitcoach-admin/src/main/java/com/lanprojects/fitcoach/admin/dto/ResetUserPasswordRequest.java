package com.lanprojects.fitcoach.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * admin 后台重置用户密码请求体。
 *
 * <p>独立接口（{@code POST /api/admin/users/{uid}/reset-password}），与"修改基础信息"分离，
 * 便于 audit log 单独记一条 {@code RESET_USER_PASSWORD}，回查清晰。
 */
@Data
public class ResetUserPasswordRequest {

    /** 新密码（6-64 位；admin 端只做长度校验） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String password;
}
