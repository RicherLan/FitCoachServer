package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户号（{@code account}）生成器：8 位纯数字、首位 1-9（避免视觉歧义且不以 0 开头）。
 *
 * <p>容量评估：首位 9 种 × 后 7 位 10^7 种 = 9 × 10^7 = 9000 万个可用账号，
 * 在百万 DAU 量级前完全够用；冲突率随用户数线性上升，但短期内重试 1~2 次即可。
 *
 * <p>本工具只负责"取一个数据库里当前不存在的号"，不负责把号写到 user 表
 * （由调用方在同一事务里调用 {@link UserRepository#save(Object)}）。
 * 因此存在「生成 → save 之间窗口期被并发抢占」的理论可能 —— 实际由 DB 唯一索引
 * {@code uk_account} 保底，save 抛 {@link org.springframework.dao.DataIntegrityViolationException}
 * 时由调用方上层捕获重试即可（极小概率事件）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountGenerator {

    /** 最大尝试次数 —— 在 9000 万容量被填满 50% 之前，N 次内必能成功（N 通常 < 3） */
    private static final int MAX_ATTEMPTS = 16;

    private final UserRepository userRepository;

    /**
     * 生成一个数据库中当前不存在的 account（8 位纯数字、首位 1-9）。
     *
     * @return 8 位 account 字符串
     * @throws BusinessException 若超过 {@link #MAX_ATTEMPTS} 次仍冲突（生产环境意味着号段需扩容）
     */
    public String generateUnique() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String candidate = generate(rnd);
            if (!userRepository.existsByAccount(candidate)) {
                if (attempt > 1) {
                    log.info("AccountGenerator collision resolved, attempt={}, account={}", attempt, candidate);
                }
                return candidate;
            }
        }
        log.error("AccountGenerator failed to find unique account after {} attempts —— account pool may be saturated",
                MAX_ATTEMPTS);
        throw new BusinessException(ResultCode.ACCOUNT_GENERATION_FAILED);
    }

    /** 生成一个 8 位字符串：首位 1-9，后 7 位 0-9 */
    private static String generate(ThreadLocalRandom rnd) {
        int first = rnd.nextInt(1, 10);       // 1..9
        int rest = rnd.nextInt(0, 10_000_000); // 0..9_999_999
        return first + String.format("%07d", rest);
    }
}
