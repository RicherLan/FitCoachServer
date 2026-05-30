package com.lanprojects.fitcoach.clientbus.controller;

import com.lanprojects.fitcoach.common.clientbus.ClientPollContribution;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.service.AuthService;
import com.lanprojects.fitcoach.login.service.UserActivityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户端通用轮询入口：{@code GET /api/client/poll}
 *
 * <p>设计目标：把多种"服务端推、客户端拉"能力（日志拉取 / 版本更新 / 远程开关 /
 * 风控强制下线 / 站内信 等）聚合到单接口，降低客户端轮询开销与服务端 QPS。
 *
 * <p><b>扩展方式</b>：业务模块只需新增一个 {@link ClientPollContribution} Spring Bean
 * 即可自动出现在响应里，无需改动本 controller。
 *
 * <p><b>响应结构</b>（所有字段均为可选；为 null 时不会出现）：
 * <pre>{@code
 * {
 *   "code": 0, "message": "OK",
 *   "data": {
 *     "logTask":       { "taskId": 123, ... } | 缺失,   // 来自 fitcoach-log
 *     "versionUpdate": { ... }                | 缺失,   // 未来：版本更新提示
 *     "configPatch":   { ... }                | 缺失,   // 未来：远程开关 / Feature Flag
 *     "forceLogout":   { ... }                | 缺失,   // 未来：风控强制下线
 *     "messages":      [ ... ]                | 缺失,   // 未来：站内信
 *     "serverTime":    1717000000000                  // 服务器毫秒时间，给客户端时钟矫正用
 *   }
 * }
 * }</pre>
 *
 * <p><b>心跳</b>：本接口在每次调用时调 {@link UserActivityService#touch(String)} 刷新用户活跃时间，
 * 让所有未来加入的能力自动获得"在线状态上报"，不需要每个业务接口各自调一遍。
 */
@Slf4j
@Tag(name = "客户端-通用轮询", description = "聚合服务端推（日志拉取/版本/远程开关/强制下线/站内信）的单一入口")
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
public class ClientPollController {

    /** {@link #SERVER_TIME_KEY} 是 controller 内置字段，contribution 不得占用同名 key */
    private static final String SERVER_TIME_KEY = "serverTime";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final UserActivityService userActivityService;
    /** Spring 自动注入所有 {@link ClientPollContribution} 实现 */
    private final List<ClientPollContribution> contributions;

    /**
     * 启动期校验 contribution 的 key 是否唯一 / 是否与内置字段冲突。
     * <p>不写到运行期：避免每次轮询都重算；启动失败比线上跑歪好排查。
     */
    @PostConstruct
    void validateContributions() {
        Set<String> seen = new HashSet<>();
        for (ClientPollContribution c : contributions) {
            String key = c.key();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException(
                        "ClientPollContribution key 不能为空: " + c.getClass().getName());
            }
            if (SERVER_TIME_KEY.equals(key)) {
                throw new IllegalStateException(
                        "ClientPollContribution key='" + key + "' 与内置字段冲突: " + c.getClass().getName());
            }
            if (!seen.add(key)) {
                throw new IllegalStateException(
                        "ClientPollContribution key 重复: '" + key + "' from " + c.getClass().getName());
            }
        }
        log.info("ClientPollController 装配完成, contributions={}, keys={}",
                contributions.size(), seen);
    }

    /**
     * 客户端通用轮询。
     * <p>所有 contribution 串行执行；任一抛异常仅记 warn，不会污染其他字段。
     */
    @GetMapping("/poll")
    public Result<Map<String, Object>> poll(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String uid = currentUid(authorization);
        userActivityService.touch(uid);

        // LinkedHashMap 保留 contribution 注册顺序，方便排查问题（响应字段顺序稳定）
        Map<String, Object> payload = new LinkedHashMap<>();
        for (ClientPollContribution c : contributions) {
            String key = c.key();
            try {
                Object value = c.resolve(uid);
                if (value != null) {
                    payload.put(key, value);
                }
            } catch (Exception e) {
                // 单个 contribution 失败不影响其他能力，整体接口仍返回 200
                log.warn("client-poll contribution[{}] 解析失败 uid={}: {}", key, uid, e.getMessage());
            }
        }
        // 内置字段：服务器时间，给客户端做时钟矫正用（避免客户端时间漂移导致 expireAtMillis 误判）
        payload.put(SERVER_TIME_KEY, System.currentTimeMillis());
        return Result.success(payload);
    }

    // ====== 鉴权 ======

    private String currentUid(String authorization) {
        return authService.getCurrentUser(extractToken(authorization)).getUid();
    }

    private String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少 Authorization 请求头");
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authorization 必须以 'Bearer ' 开头");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authorization 中的 token 为空");
        }
        return token;
    }
}
