package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.trainingexercise.AdminTrainingExerciseDto;
import com.lanprojects.fitcoach.admin.dto.trainingexercise.AdminTrainingExerciseRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingExercise;
import com.lanprojects.fitcoach.trainingrecord.service.TrainingExerciseService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 训练动作库管理（admin 后台）。
 *
 * <p>路径前缀：{@code /api/admin/training-exercises}
 * （注意复数 s，与 {@code /api/admin/exercises} 命名风格一致；客户端用单数 {@code /api/training-exercise}）。
 *
 * <ul>
 *   <li>{@code GET /} —— 列表（含禁用），按 sortOrder 升序；只返回内置动作（userId IS NULL）；</li>
 *   <li>{@code GET /{id}} —— 详情</li>
 *   <li>{@code POST /} —— 创建内置动作</li>
 *   <li>{@code PATCH /{id}} —— 更新（PATCH 语义：null = 不动）；exerciseKey 不可改</li>
 *   <li>{@code POST /{id}/toggle-enabled?value=true|false} —— 一键启用/禁用</li>
 *   <li>{@code DELETE /{id}} —— 硬删（历史训练记录因有快照字段不受影响）</li>
 * </ul>
 *
 * <p>权限走 {@code AdminAuthInterceptor}（路径 {@code /api/admin/**} 自动拦截 + 校验 admin token）。
 * 所有写操作通过 {@link AdminAuditLogService} 写审计日志。
 */
@Slf4j
@Tag(name = "后台-训练动作库", description = "训练动作 CRUD + 启停（与客户端 AI 动作 /api/admin/exercises 独立）")
@RestController
@RequestMapping("/api/admin/training-exercises")
@RequiredArgsConstructor
public class AdminTrainingExerciseController {

    private final TrainingExerciseService trainingExerciseService;
    private final AdminAuditLogService auditLogService;

    /** 列表（仅内置动作 + 含禁用） */
    @GetMapping
    public Result<List<AdminTrainingExerciseDto>> list() {
        List<AdminTrainingExerciseDto> records = trainingExerciseService.listAllBuiltin().stream()
                .map(AdminTrainingExerciseDto::from)
                .toList();
        return Result.success(records);
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<AdminTrainingExerciseDto> detail(@PathVariable("id") Long id) {
        TrainingExercise exercise = trainingExerciseService.findById(id);
        return Result.success(AdminTrainingExerciseDto.from(exercise));
    }

    /** 创建内置动作 */
    @PostMapping
    public Result<AdminTrainingExerciseDto> create(
            HttpServletRequest request,
            @Validated(AdminTrainingExerciseRequest.OnCreate.class) @RequestBody AdminTrainingExerciseRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            TrainingExercise saved = trainingExerciseService.createBuiltin(body.toCreateEntity());
            log.info("[admin] {} 创建训练动作 id={} key={}", operator, saved.getId(), saved.getExerciseKey());
            auditLogService.logSuccess(request, AdminAuditAction.CREATE_TRAINING_EXERCISE,
                    "TRAINING_EXERCISE", String.valueOf(saved.getId()),
                    String.format("key=%s, displayName=%s, muscleGroup=%s, equipment=%s, enabled=%s",
                            saved.getExerciseKey(), saved.getDisplayName(),
                            saved.getMuscleGroup(), saved.getEquipment(), saved.getEnabled()));
            return Result.success(AdminTrainingExerciseDto.from(saved));
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.CREATE_TRAINING_EXERCISE,
                    "TRAINING_EXERCISE", null,
                    String.format("key=%s", body.getExerciseKey()), e.getMessage());
            throw e;
        }
    }

    /** 更新（PATCH 语义） */
    @PatchMapping("/{id}")
    public Result<AdminTrainingExerciseDto> update(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @Validated(AdminTrainingExerciseRequest.OnUpdate.class) @Valid @RequestBody AdminTrainingExerciseRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            TrainingExercise updated = trainingExerciseService.update(id, body.toPatchEntity());
            log.info("[admin] {} 更新训练动作 id={} enabled={}",
                    operator, updated.getId(), updated.getEnabled());
            auditLogService.logSuccess(request, AdminAuditAction.UPDATE_TRAINING_EXERCISE,
                    "TRAINING_EXERCISE", String.valueOf(id),
                    String.format("enabled=%s", updated.getEnabled()));
            return Result.success(AdminTrainingExerciseDto.from(updated));
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.UPDATE_TRAINING_EXERCISE,
                    "TRAINING_EXERCISE", String.valueOf(id), "patch update", e.getMessage());
            throw e;
        }
    }

    /** 一键启用/禁用 */
    @PostMapping("/{id}/toggle-enabled")
    public Result<AdminTrainingExerciseDto> toggleEnabled(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestParam("value") boolean value) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            TrainingExercise patch = new TrainingExercise();
            patch.setEnabled(value);
            TrainingExercise updated = trainingExerciseService.update(id, patch);
            log.info("[admin] {} 切换训练动作 id={} 启用状态 → {}", operator, id, value);
            auditLogService.logSuccess(request, AdminAuditAction.UPDATE_TRAINING_EXERCISE,
                    "TRAINING_EXERCISE", String.valueOf(id),
                    String.format("toggle enabled=%s", value));
            return Result.success(AdminTrainingExerciseDto.from(updated));
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.UPDATE_TRAINING_EXERCISE,
                    "TRAINING_EXERCISE", String.valueOf(id),
                    String.format("toggle enabled=%s", value), e.getMessage());
            throw e;
        }
    }

    /** 硬删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable("id") Long id) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            trainingExerciseService.delete(id);
            log.info("[admin] {} 删除训练动作 id={}", operator, id);
            auditLogService.logSuccess(request, AdminAuditAction.DELETE_TRAINING_EXERCISE,
                    "TRAINING_EXERCISE", String.valueOf(id), "hard delete");
            return Result.success(null);
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.DELETE_TRAINING_EXERCISE,
                    "TRAINING_EXERCISE", String.valueOf(id), "hard delete", e.getMessage());
            throw e;
        }
    }
}
