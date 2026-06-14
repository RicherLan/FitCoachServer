package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.notification.AdminSystemNotificationDto;
import com.lanprojects.fitcoach.admin.dto.notification.AdminSystemNotificationRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.notification.entity.NotificationStatus;
import com.lanprojects.fitcoach.notification.entity.SystemNotificationEntity;
import com.lanprojects.fitcoach.notification.service.SystemNotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统通知（站内弹窗）管理（admin 后台）。
 *
 * <p>路径前缀：{@code /api/admin/system-notifications}
 * <ul>
 *   <li>{@code GET /} —— 列表（可按 status 过滤），按 created_at 倒序</li>
 *   <li>{@code GET /{id}} —— 详情</li>
 *   <li>{@code POST /} —— 创建（默认 DRAFT，需后续 toggle-status 或 update 切到 PUBLISHED）</li>
 *   <li>{@code PATCH /{id}} —— 更新（PATCH 语义）</li>
 *   <li>{@code POST /{id}/toggle-status?value=PUBLISHED|DRAFT|ARCHIVED} —— 一键改状态</li>
 *   <li>{@code DELETE /{id}} —— 硬删除</li>
 * </ul>
 *
 * <p><b>鉴权</b>：走 {@link AdminAuthInterceptor}，仅 admin 可访问；客户端的拉取入口在
 * {@code /api/client/poll}（systemNotification 字段），与此处隔离。
 */
@Slf4j
@Tag(name = "后台-系统通知管理", description = "维护站内弹窗（全员/指定用户、过期天数、双按钮 deeplink）")
@RestController
@RequestMapping("/api/admin/system-notifications")
@RequiredArgsConstructor
public class AdminSystemNotificationController {

    private final SystemNotificationService notificationService;
    private final AdminAuditLogService auditLogService;

    /** 列表（可按 status 过滤） */
    @GetMapping
    public Result<List<AdminSystemNotificationDto>> list(
            @RequestParam(value = "status", required = false) NotificationStatus status) {
        List<SystemNotificationEntity> rows = (status != null)
                ? notificationService.listByStatus(status)
                : notificationService.listAll();
        return Result.success(rows.stream().map(AdminSystemNotificationDto::from).toList());
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<AdminSystemNotificationDto> detail(@PathVariable("id") Long id) {
        return Result.success(AdminSystemNotificationDto.from(notificationService.findById(id)));
    }

    /** 创建（默认 DRAFT；admin 切换到 PUBLISHED 后才会下发给客户端） */
    @PostMapping
    public Result<AdminSystemNotificationDto> create(
            HttpServletRequest request,
            @Validated(AdminSystemNotificationRequest.OnCreate.class) @RequestBody AdminSystemNotificationRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        SystemNotificationEntity saved = notificationService.create(
                body.toCreateEntity(), body.getExpireDays(), operator);
        log.info("[admin] {} 创建系统通知 id={} status={} audience={} expireDays={}",
                operator, saved.getId(), saved.getStatus(), saved.getTargetAudience(), body.getExpireDays());
        auditLogService.logSuccess(request, AdminAuditAction.CREATE_SYS_NOTIFICATION,
                "SYS_NOTIFICATION", String.valueOf(saved.getId()),
                String.format("title=%s, status=%s, audience=%s, expireDays=%d",
                        saved.getTitle(), saved.getStatus(), saved.getTargetAudience(), body.getExpireDays()));
        return Result.success(AdminSystemNotificationDto.from(saved));
    }

    /** 更新（PATCH 语义） */
    @PatchMapping("/{id}")
    public Result<AdminSystemNotificationDto> update(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminSystemNotificationRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        SystemNotificationEntity updated = notificationService.update(
                id, body.toPatchEntity(), body.getExpireDays());
        log.info("[admin] {} 更新系统通知 id={} status={} audience={}",
                operator, id, updated.getStatus(), updated.getTargetAudience());
        auditLogService.logSuccess(request, AdminAuditAction.UPDATE_SYS_NOTIFICATION,
                "SYS_NOTIFICATION", String.valueOf(id),
                String.format("title=%s, status=%s, audience=%s, expireDays=%s",
                        updated.getTitle(), updated.getStatus(), updated.getTargetAudience(),
                        body.getExpireDays() != null ? body.getExpireDays() : "(unchanged)"));
        return Result.success(AdminSystemNotificationDto.from(updated));
    }

    /** 一键改状态（DRAFT / PUBLISHED / ARCHIVED） */
    @PostMapping("/{id}/toggle-status")
    public Result<AdminSystemNotificationDto> toggleStatus(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestParam("value") NotificationStatus value) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        SystemNotificationEntity patch = new SystemNotificationEntity();
        patch.setStatus(value);
        SystemNotificationEntity updated = notificationService.update(id, patch, null);
        log.info("[admin] {} 切换系统通知 id={} status → {}", operator, id, value);
        auditLogService.logSuccess(request, AdminAuditAction.UPDATE_SYS_NOTIFICATION,
                "SYS_NOTIFICATION", String.valueOf(id),
                String.format("toggle status=%s, title=%s", value, updated.getTitle()));
        return Result.success(AdminSystemNotificationDto.from(updated));
    }

    /** 硬删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable("id") Long id) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        notificationService.delete(id);
        log.info("[admin] {} 删除系统通知 id={}", operator, id);
        auditLogService.logSuccess(request, AdminAuditAction.DELETE_SYS_NOTIFICATION,
                "SYS_NOTIFICATION", String.valueOf(id), "hard delete");
        return Result.success(null);
    }
}
