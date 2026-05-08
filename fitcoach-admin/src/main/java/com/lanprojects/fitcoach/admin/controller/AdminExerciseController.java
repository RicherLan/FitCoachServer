package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.dto.exercise.AdminExerciseDto;
import com.lanprojects.fitcoach.admin.dto.exercise.AdminExerciseRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.exercise.entity.Exercise;
import com.lanprojects.fitcoach.exercise.service.ExerciseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 健身动作管理（admin 后台）。
 *
 * <p>路径前缀：{@code /api/admin/exercises}
 * <ul>
 *   <li>{@code GET /} —— 列表（含禁用动作）</li>
 *   <li>{@code GET /{id}} —— 详情</li>
 *   <li>{@code POST /} —— 创建</li>
 *   <li>{@code PATCH /{id}} —— 更新（PATCH 语义：null = 不动）</li>
 *   <li>{@code POST /{id}/toggle-free?value=true|false} —— 切换免费/付费</li>
 *   <li>{@code POST /{id}/toggle-enabled?value=true|false} —— 启用/禁用</li>
 * </ul>
 *
 * <p>"每个肌群至少保留一个免费动作"的保护规则由
 * {@link ExerciseService#update} 内部强制（违反返回 7504 EXERCISE_LAST_FREE_IN_GROUP）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/exercises")
@RequiredArgsConstructor
public class AdminExerciseController {

    private final ExerciseService exerciseService;

    /** 列表（含禁用），按 sortOrder 升序 */
    @GetMapping
    public Result<List<AdminExerciseDto>> list() {
        List<AdminExerciseDto> records = exerciseService.listAll().stream()
                .map(AdminExerciseDto::from)
                .toList();
        return Result.success(records);
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<AdminExerciseDto> detail(@PathVariable("id") Long id) {
        Exercise exercise = exerciseService.findById(id);
        return Result.success(AdminExerciseDto.from(exercise));
    }

    /** 创建动作 */
    @PostMapping
    public Result<AdminExerciseDto> create(
            HttpServletRequest request,
            @Validated(AdminExerciseRequest.OnCreate.class) @RequestBody AdminExerciseRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        Exercise saved = exerciseService.create(body.toCreateEntity());
        log.info("[admin] {} 创建动作 id={} key={}", operator, saved.getId(), saved.getExerciseKey());
        return Result.success(AdminExerciseDto.from(saved));
    }

    /** 更新（PATCH 语义） */
    @PatchMapping("/{id}")
    public Result<AdminExerciseDto> update(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @Validated(AdminExerciseRequest.OnUpdate.class) @Valid @RequestBody AdminExerciseRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        Exercise updated = exerciseService.update(id, body.toPatchEntity());
        log.info("[admin] {} 更新动作 id={} isFree={} enabled={}",
                operator, updated.getId(), updated.getIsFree(), updated.getEnabled());
        return Result.success(AdminExerciseDto.from(updated));
    }

    /** 一键切换免费/付费（独立接口，前端 Switch 直接打勾） */
    @PostMapping("/{id}/toggle-free")
    public Result<AdminExerciseDto> toggleFree(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestParam("value") boolean value) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        Exercise patch = new Exercise();
        patch.setIsFree(value);
        Exercise updated = exerciseService.update(id, patch);
        log.info("[admin] {} 切换动作 id={} 免费状态 → {}", operator, id, value);
        return Result.success(AdminExerciseDto.from(updated));
    }

    /** 一键启用/禁用 */
    @PostMapping("/{id}/toggle-enabled")
    public Result<AdminExerciseDto> toggleEnabled(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestParam("value") boolean value) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        Exercise patch = new Exercise();
        patch.setEnabled(value);
        Exercise updated = exerciseService.update(id, patch);
        log.info("[admin] {} 切换动作 id={} 启用状态 → {}", operator, id, value);
        return Result.success(AdminExerciseDto.from(updated));
    }
}
