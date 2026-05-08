package com.lanprojects.fitcoach.exercise.controller;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.exercise.dto.ExerciseDTO;
import com.lanprojects.fitcoach.exercise.service.ExerciseService;
import com.lanprojects.fitcoach.login.support.AuthSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 健身动作控制器（用户端）。
 *
 * <p>接口前缀：/api/exercise
 *
 * <p>当前只有"列表"接口。运营在 admin 后台维护动作（{@code /api/admin/exercise/...} 在 admin 模块），
 * 客户端通过本接口拉取最新动作元数据，不再依赖 RN 端硬编码（原 exercises.ts 已被 server 接管）。
 *
 * <p><b>会员/付费控制</b>：本接口对所有登录用户返回**全部**启用动作（含付费），由列表项的
 * {@link ExerciseDTO#getIsFree() isFree} 字段告诉客户端"是否免费"，客户端据此打标签 +
 * 在用户点击付费动作时弹会员引导。**真正的能力网关**（拒绝非会员实际调用付费动作的训练能力）
 * 由各业务接口在自己内部走 MembershipService.requireMembership 校验，本接口不做拦截。
 */
@Slf4j
@RestController
@RequestMapping("/api/exercise")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final AuthSupport auth;

    /**
     * 拿启用动作列表（已按 sortOrder 升序）。
     * <p>需登录（避免未登录爬动作元数据），但不需要会员。
     */
    @GetMapping("/list")
    public Result<List<ExerciseDTO>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // 鉴权：只验登录，不验会员
        auth.requireUserId(authorization);

        List<ExerciseDTO> data = exerciseService.listEnabled().stream()
                .map(ExerciseDTO::from)
                .toList();
        return Result.success(data);
    }
}
