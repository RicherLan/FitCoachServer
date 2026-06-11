package com.lanprojects.fitcoach.track.controller;

import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.login.service.AuthService;
import com.lanprojects.fitcoach.track.dto.TrackEventBatchRequest;
import com.lanprojects.fitcoach.track.service.TrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 客户端埋点接口（产品分析）。
 *
 * <p>前缀：{@code /api/track}（已落在 ClientInfoInterceptor 覆盖范围内，
 * deviceId / platform / version / lang 等基础信息会被自动注入到 {@code ClientContext}）。
 *
 * <p><b>鉴权策略</b>：
 * <ul>
 *   <li><b>不强制登录</b> —— 未登录用户的浏览行为（首页停留、点击注册按钮）对漏斗分析很有价值；</li>
 *   <li>有 token 就尝试解析 uid 关联用户；解析失败（token 过期 / 非法）静默忽略，
 *       <b>不抛 401</b> —— 否则客户端 SDK 反复重试会浪费流量。</li>
 * </ul>
 *
 * <p><b>限流策略</b>：service 层做 deviceId 维度 200 批次/分钟限流（{@link TrackService#receiveBatch}）。
 */
@Slf4j
@Tag(name = "客户端-埋点", description = "产品行为埋点批量上报（未登录用户也可上报）")
@RestController
@RequestMapping("/api/track")
@RequiredArgsConstructor
public class TrackController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final TrackService trackService;

    /**
     * 批量上报埋点事件。
     *
     * <p>POST /api/track/batch
     * <br>Body: {@link TrackEventBatchRequest}
     * <br>Returns: 实际入库的事件数（{@code data} 字段）
     *
     * <p><b>客户端节奏</b>：内置队列 ≥ 20 条或 ≥ 30 秒任一触发刷盘；
     * App 进后台 / 关键事件（如支付完成）强制 flush。
     */
    @Operation(summary = "批量上报埋点", description = "未登录用户可调用；deviceId / 平台 / 版本从 header 自动取")
    @PostMapping("/batch")
    public Result<Integer> batch(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody TrackEventBatchRequest request) {
        String uid = tryResolveUid(authorization);
        int received = trackService.receiveBatch(uid, request);
        return Result.success(received);
    }

    /**
     * 软解析当前 uid：解析成功返回 uid，失败（含未传 header）返回 null。
     * <p>埋点接口不强制登录，所有 token 异常均吞掉。
     */
    private String tryResolveUid(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return null;
        }
        try {
            return authService.getCurrentUser(token).getUid();
        } catch (Exception e) {
            // token 过期 / 非法 / 用户已删除 等 — 静默忽略，按未登录处理
            if (log.isDebugEnabled()) {
                log.debug("埋点接口 token 解析失败，按未登录处理: {}", e.getMessage());
            }
            return null;
        }
    }
}
