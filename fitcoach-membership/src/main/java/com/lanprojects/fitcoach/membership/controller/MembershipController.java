package com.lanprojects.fitcoach.membership.controller;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.login.support.AuthSupport;
import com.lanprojects.fitcoach.membership.dto.MembershipPlanDTO;
import com.lanprojects.fitcoach.membership.dto.MembershipStatusDTO;
import com.lanprojects.fitcoach.membership.entity.MembershipPlan;
import com.lanprojects.fitcoach.membership.service.MembershipService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会员控制器（用户端）。
 *
 * <p>接口前缀：/api/membership
 *
 * <p>**只读接口** —— 写操作（激活会员）由 fitcoach-payment 通过 PaymentSucceededEvent 触发，
 * Admin 手工赠送在 admin 模块。
 *
 * <p><b>关于鉴权</b>：
 * <ul>
 *   <li>{@code GET /plans}（套餐列表）：弱鉴权 —— 未登录也能看价格（产品决策：让落地页能直接看见价格）；</li>
 *   <li>{@code GET /my-status}（当前会员）：必须登录。</li>
 * </ul>
 */
@Slf4j
@Tag(name = "客户端-会员", description = "查可购套餐 + 查自己的会员状态（只读）")
@RestController
@RequestMapping("/api/membership")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;
    private final AuthSupport auth;

    /**
     * 拿当前在售的会员套餐（已按 sortOrder 升序）。
     * <p>未登录也可调，便于落地页 / 引导页提前展示价格。
     */
    @GetMapping("/plans")
    public Result<List<MembershipPlanDTO>> plans() {
        List<MembershipPlanDTO> data = membershipService.listEnabledPlans().stream()
                .map(MembershipPlanDTO::from)
                .toList();
        return Result.success(data);
    }

    /**
     * 拿当前用户的会员状态。
     * <p>从未开通过 → 返回 {@code {isActive: false, ...全 null}}。
     * <p>已开通但已过期 → 仍然返回历史 planCode / expiresAt 等，{@code isActive=false}，便于客户端展示"会员已到期，去续费"。
     */
    @GetMapping("/my-status")
    public Result<MembershipStatusDTO> myStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = auth.requireUserId(authorization);
        return Result.success(membershipService.findByUserId(userId)
                .map(m -> {
                    // 拿 plan 的 displayName 一起返回（不暴露 planId 给客户端）
                    String displayName = membershipService.listAllPlans().stream()
                            .filter(p -> p.getPlanCode().equals(m.getPlanCode()))
                            .map(MembershipPlan::getDisplayName)
                            .findFirst()
                            .orElse(null);
                    return MembershipStatusDTO.from(m, displayName);
                })
                .orElseGet(MembershipStatusDTO::nonMember));
    }

    /**
     * （便捷接口）一次拿"套餐 + 我的状态"，减少 RN 端会员中心页的串行 RTT。
     */
    @GetMapping("/center")
    public Result<Map<String, Object>> center(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = auth.optionalUserId(authorization);
        List<MembershipPlanDTO> plans = membershipService.listEnabledPlans().stream()
                .map(MembershipPlanDTO::from)
                .toList();
        MembershipStatusDTO status;
        if (userId == null) {
            status = MembershipStatusDTO.nonMember();
        } else {
            // 复用 myStatus 的逻辑（避免 RN 端两次接口 N+1）
            Map<String, String> codeToName = membershipService.listAllPlans().stream()
                    .collect(Collectors.toMap(MembershipPlan::getPlanCode,
                            MembershipPlan::getDisplayName,
                            (a, b) -> a));
            status = membershipService.findByUserId(userId)
                    .map(m -> MembershipStatusDTO.from(m, codeToName.get(m.getPlanCode())))
                    .orElseGet(MembershipStatusDTO::nonMember);
        }
        return Result.success(Map.of(
                "plans", plans,
                "status", status
        ));
    }
}
