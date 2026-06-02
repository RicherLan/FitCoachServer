package com.lanprojects.fitcoach.trainingrecord.controller;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.support.AuthSupport;
import com.lanprojects.fitcoach.trainingrecord.dto.TrainingRecordDTO;
import com.lanprojects.fitcoach.trainingrecord.dto.TrainingRecordRequest;
import com.lanprojects.fitcoach.trainingrecord.dto.TrainingRecordSummary;
import com.lanprojects.fitcoach.trainingrecord.entity.TrainingRecord;
import com.lanprojects.fitcoach.trainingrecord.service.TrainingRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 训练记录用户端控制器。
 *
 * <p>接口前缀：{@code /api/training-record}
 *
 * <p><b>权限</b>：所有接口都需要登录（普通用户 token），无需会员（MVP 全免费）。
 * 所有写操作都按 {@code (userId, clientId)} 幂等；所有读操作都自动按 userId 过滤防 IDOR。
 *
 * <p><b>5 个端点</b>：
 * <ul>
 *   <li>POST {@code /api/training-record} —— 创建或按 clientId 幂等更新；</li>
 *   <li>GET {@code /api/training-record} —— 分页列表（轻量 Summary，按 date desc）；</li>
 *   <li>GET {@code /api/training-record/{id}} —— 详情（完整 sets）；</li>
 *   <li>PUT {@code /api/training-record/{id}} —— 更新（同样走 createOrReplace 路径，path id 用作权限校验）；</li>
 *   <li>DELETE {@code /api/training-record/{id}} —— 硬删（带权限校验）。</li>
 * </ul>
 *
 * <p><b>幂等约定</b>：POST 与 PUT 都用 {@link TrainingRecordService#createOrReplace(Long, TrainingRecordRequest)}，
 * 客户端无论用哪个动词、提交多少次，最终落库只有 1 条 (userId, clientId) 记录。
 * 区别在于 PUT 必须带 path id 且要校验 id 与 clientId 指向同一条记录（防止越权）。
 */
@Slf4j
@Tag(name = "客户端-训练记录", description = "用户手动写下的训练日志（按 clientId 幂等）")
@RestController
@RequestMapping("/api/training-record")
@RequiredArgsConstructor
public class TrainingRecordController {

    private final TrainingRecordService trainingRecordService;
    private final AuthSupport auth;

    @PostMapping
    @Operation(summary = "创建/幂等更新训练记录",
            description = "按 (userId, clientId) 幂等：同一 clientId 重复提交会更新现有记录")
    public Result<TrainingRecordDTO> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody TrainingRecordRequest request) {
        Long userId = auth.requireUserId(authorization);
        TrainingRecord saved = trainingRecordService.createOrReplace(userId, request);
        return Result.success(TrainingRecordDTO.from(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新训练记录",
            description = "按 path id 定位 + 校验归属，body 走 createOrReplace 全量替换语义")
    public Result<TrainingRecordDTO> update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody TrainingRecordRequest request) {
        Long userId = auth.requireUserId(authorization);

        // 防越权：path id 必须属于当前用户且 clientId 一致
        TrainingRecord existing = trainingRecordService.findByIdForUser(id, userId);
        if (request.getClientId() == null
                || !request.getClientId().equals(existing.getClientId())) {
            throw new BusinessException(ResultCode.TRAINING_RECORD_FORBIDDEN);
        }

        TrainingRecord saved = trainingRecordService.createOrReplace(userId, request);
        return Result.success(TrainingRecordDTO.from(saved));
    }

    @GetMapping
    @Operation(summary = "训练记录分页列表",
            description = "按 date desc, id desc 排序；返回轻量 Summary（无 sets 详情）")
    public Result<Page<TrainingRecordSummary>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(description = "页码（0 起始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小（1-100）") @RequestParam(defaultValue = "20") int size) {
        Long userId = auth.requireUserId(authorization);
        Page<TrainingRecord> data = trainingRecordService.pageByUser(userId, page, size);
        return Result.success(data.map(TrainingRecordSummary::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "训练记录详情",
            description = "返回完整三层结构（exercises + sets），自动校验归属")
    public Result<TrainingRecordDTO> get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        Long userId = auth.requireUserId(authorization);
        TrainingRecord record = trainingRecordService.findByIdForUser(id, userId);
        return Result.success(TrainingRecordDTO.from(record));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除训练记录", description = "硬删，子表级联删除；带归属校验防 IDOR")
    public Result<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        Long userId = auth.requireUserId(authorization);
        trainingRecordService.delete(id, userId);
        return Result.success();
    }
}
