package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import com.lanprojects.fitcoach.common.config.repository.SysConfigRepository;
import com.lanprojects.fitcoach.login.service.AppleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Apple Sign In 相关 {@code sys_config} 默认值播种（阶段 3B 波 1）。
 *
 * <p>与 {@link PaymentConfigSeeder} 的 {@code apple.*}（IAP 收据校验）严格隔离 ——
 * 本 Seeder 只关心"客户端 identityToken 登录校验"所需配置：
 * <ul>
 *   <li>{@link AppleService#CONFIG_ENABLED}    —— 总开关，默认 {@code false}，
 *       避免"运维忘配 audience 就意外接受一切"这种潜在越权风险；</li>
 *   <li>{@link AppleService#CONFIG_CLIENT_IDS} —— 允许的 audience 列表（逗号分隔），
 *       默认为空字符串（占位），运维在 admin 后台填入 iOS Bundle ID 后开启。</li>
 * </ul>
 *
 * <p><b>为什么用 @Order(21)</b>：紧跟在 {@link PaymentConfigSeeder}（@Order(20)）之后执行，
 * 保持登录/支付 Seeder 顺序有序，便于日志按顺序阅读；不影响功能正确性。
 *
 * <p><b>与 apple.* 配置互不覆盖</b>：即使运维在 admin 后台复用了同一个 Bundle ID，
 * 也必须分别录入到 {@code apple.bundle_id}（IAP）与 {@code apple_login.client_ids}（登录），
 * 防止一处误改影响另一处。
 */
@Slf4j
@Order(21)
@Component
@RequiredArgsConstructor
public class AppleLoginConfigSeeder implements CommandLineRunner {

    private final SysConfigRepository sysConfigRepository;

    @Override
    public void run(String... args) {
        int inserted = 0;

        inserted += ensureExists(new SysConfig(
                AppleService.CONFIG_ENABLED, "false", "auth",
                "Apple Sign In 登录总开关（生产环境需配好 client_ids 后再开启）"));

        inserted += ensureExists(new SysConfig(
                AppleService.CONFIG_CLIENT_IDS, "", "auth",
                "Apple Sign In 允许的 audience 列表（逗号分隔，如 iOS Bundle ID / Web Services ID）"));

        if (inserted > 0) {
            log.info("[seeder] Apple 登录配置初始化完成，新增 {} 项", inserted);
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
