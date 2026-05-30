package com.lanprojects.fitcoach.exercise.controller;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.exercise.dto.MuscleGroupDTO;
import com.lanprojects.fitcoach.exercise.service.MuscleGroupService;
import com.lanprojects.fitcoach.login.support.AuthSupport;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 肌群控制器（用户端）。
 *
 * <p>接口前缀：{@code /api/muscle-group}
 *
 * <p>客户端首页类目数据完全由本接口下发——不再硬编码 MUSCLE_GROUPS / MUSCLE_GROUP_ORDER 常量。
 * 运营在 admin 后台维护肌群（{@code /api/admin/muscle-groups/...} 在 admin 模块）。
 *
 * <p>会员/付费控制：本接口不涉及会员校验（肌群本身不分免费/付费），所有登录用户可见全部启用肌群。
 */
@Slf4j
@Tag(name = "客户端-肌群", description = "拉取启用肌群列表（首页类目数据下发）")
@RestController
@RequestMapping("/api/muscle-group")
@RequiredArgsConstructor
public class MuscleGroupController {

    private final MuscleGroupService muscleGroupService;
    private final AuthSupport auth;

    /**
     * 拿启用肌群列表（已按 sortOrder 升序）。
     * <p>需登录（避免未登录爬肌群元数据），但不需要会员。
     */
    @GetMapping("/list")
    public Result<List<MuscleGroupDTO>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireUserId(authorization);

        List<MuscleGroupDTO> data = muscleGroupService.listEnabled().stream()
                .map(MuscleGroupDTO::from)
                .toList();
        return Result.success(data);
    }
}
