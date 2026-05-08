package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.dto.membership.AdminPlanDto;
import com.lanprojects.fitcoach.admin.dto.membership.AdminPlanRequest;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.membership.entity.MembershipPlan;
import com.lanprojects.fitcoach.membership.service.MembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员套餐管理（admin 后台）。
 *
 * <p>路径前缀：/api/admin/membership/plans
 * <ul>
 *   <li>{@code GET /} —— 全部套餐（含禁用），按 sortOrder 升序</li>
 *   <li>{@code GET /{id}} —— 详情</li>
 *   <li>{@code POST /} —— 创建</li>
 *   <li>{@code PATCH /{id}} —— 更新（planCode 不可改）</li>
 *   <li>{@code POST /{id}/toggle?enabled=true|false} —— 上下架</li>
 * </ul>
 *
 * <p>**没有删除接口**：套餐一旦发布有订单就不能删（违反订单关联），只允许下架。
 * 强制删除需运营走 SQL 后门。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/membership/plans")
@RequiredArgsConstructor
public class AdminMembershipPlanController {

    private final MembershipService membershipService;

    /** 套餐列表（含禁用，按 sortOrder 升序） */
    @GetMapping
    public Result<List<AdminPlanDto>> list() {
        return Result.success(membershipService.listAllPlans().stream()
                .map(AdminPlanDto::from)
                .toList());
    }

    /** 套餐详情（编辑表单回填用） */
    @GetMapping("/{id}")
    public Result<AdminPlanDto> detail(@PathVariable("id") Long id) {
        return Result.success(AdminPlanDto.from(membershipService.getPlan(id)));
    }

    /** 创建套餐 */
    @PostMapping
    public Result<AdminPlanDto> create(
            @Validated(AdminPlanRequest.OnCreate.class) @RequestBody AdminPlanRequest req) {
        MembershipPlan saved = membershipService.createPlan(req.toEntity());
        return Result.success(AdminPlanDto.from(saved));
    }

    /** 更新套餐（PATCH 语义） */
    @PatchMapping("/{id}")
    public Result<AdminPlanDto> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminPlanRequest req) {
        MembershipPlan saved = membershipService.updatePlan(id, req.toPatchEntity());
        return Result.success(AdminPlanDto.from(saved));
    }

    /** 上下架（不删除） */
    @PostMapping("/{id}/toggle")
    public Result<AdminPlanDto> toggle(
            @PathVariable("id") Long id,
            @RequestParam("enabled") boolean enabled) {
        MembershipPlan patch = new MembershipPlan();
        patch.setEnabled(enabled);
        MembershipPlan saved = membershipService.updatePlan(id, patch);
        return Result.success(AdminPlanDto.from(saved));
    }
}
