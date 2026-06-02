package com.lanprojects.fitcoach.trainingrecord.controller;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.login.support.AuthSupport;
import com.lanprojects.fitcoach.trainingrecord.dto.TrainingExerciseDTO;
import com.lanprojects.fitcoach.trainingrecord.service.TrainingExerciseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 训练动作库控制器（用户端）。
 *
 * <p>接口前缀：{@code /api/training-exercise}
 *
 * <p>当前只有"列表"接口，返回：
 * <ul>
 *   <li>所有 enabled = true 的内置动作（admin 维护的 86+）；</li>
 *   <li>加上当前登录用户的 enabled = true 自定义动作（MVP 暂无自定义入口，但接口已支持）；</li>
 *   <li>按 sortOrder 升序，客户端按返回顺序展示即可，无需二次排序。</li>
 * </ul>
 *
 * <p><b>与 {@code /api/exercise/list} 的区别</b>：
 * <ul>
 *   <li>{@code /api/exercise/list}（fitcoach-exercise）—— AI 实时识别动作元数据，带 cameraSetupJson；</li>
 *   <li>{@code /api/training-exercise/list}（本接口）—— 手动训练记录用的通用动作字典，无相机配置。</li>
 * </ul>
 * 客户端按场景独立调用，互不影响。
 */
@Slf4j
@Tag(name = "客户端-训练动作库", description = "手动训练记录用的通用动作字典（86+ 内置 + 用户自定义）")
@RestController
@RequestMapping("/api/training-exercise")
@RequiredArgsConstructor
public class TrainingExerciseController {

    private final TrainingExerciseService trainingExerciseService;
    private final AuthSupport auth;

    /**
     * 拉训练动作列表。
     * <p>需要登录（避免未登录爬取动作字典）；不需要会员（MVP 全免费）。
     */
    @GetMapping("/list")
    @Operation(summary = "训练动作列表", description = "返回当前用户可见的全部训练动作（内置 + 自定义），按 sortOrder 升序")
    public Result<List<TrainingExerciseDTO>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = auth.requireUserId(authorization);

        List<TrainingExerciseDTO> data = trainingExerciseService.listVisibleForUser(userId).stream()
                .map(TrainingExerciseDTO::from)
                .toList();
        return Result.success(data);
    }
}
