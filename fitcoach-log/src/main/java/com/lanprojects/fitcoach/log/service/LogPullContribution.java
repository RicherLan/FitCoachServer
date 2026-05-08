package com.lanprojects.fitcoach.log.service;

import com.lanprojects.fitcoach.common.clientbus.ClientPollContribution;
import com.lanprojects.fitcoach.log.dto.PendingTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 日志拉取能力对客户端通用轮询通道（{@code GET /api/client/poll}）的贡献。
 *
 * <p>响应中以 {@code "logTask"} 字段输出：
 * <ul>
 *   <li>命中 → {@link PendingTaskDto}（taskId / recentHours / expireAtMillis / uploadingDeadlineMillis）</li>
 *   <li>未命中 → 字段缺失（contribution 返回 null 时，controller 不会写入响应）</li>
 * </ul>
 *
 * <p>本 Bean 只是把已有 {@link LogPullService#claimNextPending(String)} 包了一层 SPI 适配；
 * 拉取/状态机/锁的所有逻辑仍在 LogPullService 里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogPullContribution implements ClientPollContribution {

    /** 客户端 JSON 字段名。Native LogPullWorker 据此读取。 */
    public static final String KEY = "logTask";

    private final LogPullService logPullService;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Object resolve(String uid) {
        Optional<PendingTaskDto> opt = logPullService.claimNextPending(uid);
        return opt.orElse(null);
    }
}
