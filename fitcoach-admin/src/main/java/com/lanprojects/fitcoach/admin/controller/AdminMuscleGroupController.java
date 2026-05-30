package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.musclegroup.AdminMuscleGroupDto;
import com.lanprojects.fitcoach.admin.dto.musclegroup.AdminMuscleGroupRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.exercise.entity.MuscleGroupEntity;
import com.lanprojects.fitcoach.exercise.service.MuscleGroupService;
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
 * 肌群管理（admin 后台）。
 *
 * <p>路径前缀：{@code /api/admin/muscle-groups}
 * <ul>
 *   <li>{@code GET /} —— 列表（含禁用，按 sortOrder 升序）</li>
 *   <li>{@code GET /{id}} —— 详情</li>
 *   <li>{@code POST /} —— 创建</li>
 *   <li>{@code PATCH /{id}} —— 更新（PATCH 语义；groupKey 不可改）</li>
 *   <li>{@code POST /{id}/toggle-enabled?value=true|false} —— 上下架（弱保护：可直接禁用，不需先迁移动作）</li>
 *   <li>{@code DELETE /{id}} —— 硬删除（强保护：还有 Exercise 引用时返回 7603）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/muscle-groups")
@RequiredArgsConstructor
public class AdminMuscleGroupController {

    private final MuscleGroupService muscleGroupService;
    private final AdminAuditLogService auditLogService;

    /** 列表（含禁用），按 sortOrder 升序 */
    @GetMapping
    public Result<List<AdminMuscleGroupDto>> list() {
        List<AdminMuscleGroupDto> records = muscleGroupService.listAll().stream()
                .map(AdminMuscleGroupDto::from)
                .toList();
        return Result.success(records);
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<AdminMuscleGroupDto> detail(@PathVariable("id") Long id) {
        return Result.success(AdminMuscleGroupDto.from(muscleGroupService.findById(id)));
    }

    /** 创建 */
    @PostMapping
    public Result<AdminMuscleGroupDto> create(
            HttpServletRequest request,
            @Validated(AdminMuscleGroupRequest.OnCreate.class) @RequestBody AdminMuscleGroupRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            MuscleGroupEntity saved = muscleGroupService.create(body.toCreateEntity());
            log.info("[admin] {} 创建肌群 id={} key={}", operator, saved.getId(), saved.getGroupKey());
            auditLogService.logSuccess(request, AdminAuditAction.CREATE_MUSCLE_GROUP,
                    "MUSCLE_GROUP", String.valueOf(saved.getId()),
                    String.format("key=%s, displayName=%s, enabled=%s",
                            saved.getGroupKey(), saved.getDisplayName(), saved.getEnabled()));
            return Result.success(AdminMuscleGroupDto.from(saved));
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.CREATE_MUSCLE_GROUP,
                    "MUSCLE_GROUP", null,
                    String.format("key=%s", body.getGroupKey()), e.getMessage());
            throw e;
        }
    }

    /** 更新（PATCH 语义） */
    @PatchMapping("/{id}")
    public Result<AdminMuscleGroupDto> update(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminMuscleGroupRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            MuscleGroupEntity updated = muscleGroupService.update(id, body.toPatchEntity());
            log.info("[admin] {} 更新肌群 id={} key={} enabled={}",
                    operator, updated.getId(), updated.getGroupKey(), updated.getEnabled());
            auditLogService.logSuccess(request, AdminAuditAction.UPDATE_MUSCLE_GROUP,
                    "MUSCLE_GROUP", String.valueOf(id),
                    String.format("key=%s, enabled=%s", updated.getGroupKey(), updated.getEnabled()));
            return Result.success(AdminMuscleGroupDto.from(updated));
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.UPDATE_MUSCLE_GROUP,
                    "MUSCLE_GROUP", String.valueOf(id), "patch update", e.getMessage());
            throw e;
        }
    }

    /** 一键启用/禁用（弱保护：禁用即可隐藏整个类目，不需要先迁移动作） */
    @PostMapping("/{id}/toggle-enabled")
    public Result<AdminMuscleGroupDto> toggleEnabled(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestParam("value") boolean value) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            MuscleGroupEntity patch = new MuscleGroupEntity();
            patch.setEnabled(value);
            MuscleGroupEntity updated = muscleGroupService.update(id, patch);
            log.info("[admin] {} 切换肌群 id={} 启用状态 → {}", operator, id, value);
            auditLogService.logSuccess(request, AdminAuditAction.UPDATE_MUSCLE_GROUP,
                    "MUSCLE_GROUP", String.valueOf(id),
                    String.format("toggle enabled=%s", value));
            return Result.success(AdminMuscleGroupDto.from(updated));
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.UPDATE_MUSCLE_GROUP,
                    "MUSCLE_GROUP", String.valueOf(id),
                    String.format("toggle enabled=%s", value), e.getMessage());
            throw e;
        }
    }

    /** 硬删除（若仍有 Exercise 引用，会返回 7603 MUSCLE_GROUP_HAS_EXERCISES） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable("id") Long id) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            muscleGroupService.delete(id);
            log.info("[admin] {} 删除肌群 id={}", operator, id);
            auditLogService.logSuccess(request, AdminAuditAction.DELETE_MUSCLE_GROUP,
                    "MUSCLE_GROUP", String.valueOf(id), "hard delete");
            return Result.success(null);
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.DELETE_MUSCLE_GROUP,
                    "MUSCLE_GROUP", String.valueOf(id), "hard delete", e.getMessage());
            throw e;
        }
    }
}
