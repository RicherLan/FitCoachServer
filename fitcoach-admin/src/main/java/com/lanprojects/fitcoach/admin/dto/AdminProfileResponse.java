package com.lanprojects.fitcoach.admin.dto;

import lombok.Builder;
import lombok.Data;

/**
 * /api/admin/auth/me 接口返回 — 当前管理员资料
 */
@Data
@Builder
public class AdminProfileResponse {
    private String username;
    private String displayName;
    private String role;
    private Long lastLoginAt;
}
