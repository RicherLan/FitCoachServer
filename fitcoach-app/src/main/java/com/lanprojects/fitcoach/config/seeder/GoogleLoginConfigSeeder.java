package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import com.lanprojects.fitcoach.common.config.repository.SysConfigRepository;
import com.lanprojects.fitcoach.login.service.GoogleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Google Sign In 相关 {@code sys_config} 默认值播种（阶段 3B 波 2）。
 *
 * <p>与 {@link AppleLoginConfigSeeder} 对称，只关心"客户端 idToken 登录校验"所需配置：
 * <ul>
 *   <li>{@link GoogleService#CONFIG_ENABLED}    —— 总开关，默认 {@code false}，
 *       避免"运维忘配 client_ids 就意外接受任意 audience"这种潜在越权风险；</li>
 *   <li>{@link GoogleService#CONFIG_CLIENT_IDS} —— 允许的 OAuth Client ID 列表（逗号分隔），
 *       默认为空字符串（占位）。运维在 admin 后台按需填入 iOS / Android / Web 三端各自的
 *       Client ID 后再打开总开关。</li>
 * </ul>
 *
 * <p><b>为什么用 @Order(22)</b>：紧跟在 {@link AppleLoginConfigSeeder}（@Order(21)）之后执行，
 * 与 Apple 波 1 保持同一分组，便于日志按顺序阅读；不影响功能正确性。
 *
 * <p><b>iOS / Android / Web 三端 Client ID</b>：Google OAuth 要求同一个 Firebase / GCP 项目下
 * 每个平台生成独立的 Client ID，Server 只需把所有允许的 Client ID 都塞进
 * {@code google_login.client_ids}，Nimbus JWT 校验时会以 audience 集合方式命中任一即可。
 */
@Slf4j
@Order(22)
@Component
@RequiredArgsConstructor
public class GoogleLoginConfigSeeder implements CommandLineRunner {

    private final SysConfigRepository sysConfigRepository;

    @Override
    public void run(String... args) {
        int inserted = 0;

        inserted += ensureExists(new SysConfig(
                GoogleService.CONFIG_ENABLED, "false", "auth",
                "Google Sign In 登录总开关（生产环境需配好 client_ids 后再开启）"));

        inserted += ensureExists(new SysConfig(
                GoogleService.CONFIG_CLIENT_IDS, "", "auth",
                "Google Sign In 允许的 OAuth Client ID 列表（逗号分隔，iOS / Android / Web 三端各一个）"));

        if (inserted > 0) {
            log.info("[seeder] Google 登录配置初始化完成，新增 {} 项", inserted);
        }
    }

    private int ensureExists(SysConfig config) {
        if (sysConfigRepository.findByConfigKey(config.getConfigKey()).isEmpty()) {
            sysConfigRepository.save(config);
            return 1;
        }
        return 0;
    }
}
