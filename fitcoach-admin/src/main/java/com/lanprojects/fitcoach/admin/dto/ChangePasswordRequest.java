package com.lanprojects.fitcoach.admin.dto;

import lombok.Data;

/**
 * 管理员修改密码请求
 */
@Data
public class ChangePasswordRequest {
    private String oldPassword;
    private String newPassword;
}
