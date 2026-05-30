package com.lanprojects.fitcoach.admin.dto.audit;

import com.lanprojects.fitcoach.admin.audit.AdminAuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志查询返回 DTO（用于 admin 后台的"审计日志"页）。
 *
 * <p>不暴露任何敏感字段；ip / ua 仅用于辅助定位。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogDto {

    private Long id;
    private String adminUsername;
    private String adminRole;
    /** 操作枚举名（字符串） */
    private String action;
    private String targetType;
    private String targetId;
    private String summary;
    private Boolean success;
    private String errorMsg;
    private String ip;
    private String ua;
    private String requestUri;
    private LocalDateTime createdAt;

    public static AdminAuditLogDto from(AdminAuditLog e) {
        return AdminAuditLogDto.builder()
                .id(e.getId())
                .adminUsername(e.getAdminUsername())
                .adminRole(e.getAdminRole())
                .action(e.getAction() == null ? null : e.getAction().name())
                .targetType(e.getTargetType())
                .targetId(e.getTargetId())
                .summary(e.getSummary())
                .success(e.getSuccess())
                .errorMsg(e.getErrorMsg())
                .ip(e.getIp())
                .ua(e.getUa())
                .requestUri(e.getRequestUri())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
