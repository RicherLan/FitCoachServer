package com.lanprojects.fitcoach.admin.config;

import com.lanprojects.fitcoach.admin.entity.AdminRole;
import com.lanprojects.fitcoach.admin.entity.AdminUser;
import com.lanprojects.fitcoach.admin.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 后台管理首次启动初始化器 — 写入默认超管账号，并对默认密码做启动期强校验。
 * <p>
 * 设计：
 * <ul>
 *   <li>只在 admin_user 表为空时插入，避免覆盖管理员后续修改的密码；</li>
 *   <li>默认账号：username={@value #DEFAULT_USERNAME} / password={@value #DEFAULT_PASSWORD}，
 *       <b>生产环境必须立即登录后修改</b>；</li>
 *   <li>角色固定为 SUPER_ADMIN，方便后续创建其他管理员。</li>
 * </ul>
 *
 * <h3>安全护栏（P0 — 防 admin 默认密码长期未改）</h3>
 * <p>每次启动都会检查 {@value #DEFAULT_USERNAME} 用户的密码是否仍为 {@value #DEFAULT_PASSWORD}：
 * <ul>
 *   <li>{@code dev} profile：仅打 WARN，不挡启动（本地开发方便）；</li>
 *   <li>非 dev profile（如 prod / sit）：打 ERROR 并 <b>抛 IllegalStateException 拒启动</b>；</li>
 *   <li>紧急逃生：环境变量 {@code ADMIN_ALLOW_DEFAULT_PASSWORD=true} 可临时跳过校验（不推荐，仅用于灾备）。</li>
 * </ul>
 *
 * <p>这里独立一个 CommandLineRunner，不与 fitcoach-app 中的 DataInitializer 合并 —— 让
 * admin 模块的初始化逻辑跟着 admin 模块走，符合"模块自治"的多模块设计。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDataInitializer implements CommandLineRunner {

    public static final String DEFAULT_USERNAME = "admin";
    /** 默认密码 — 生产环境务必首次登录后立即修改 */
    public static final String DEFAULT_PASSWORD = "admin123";
    public static final String DEFAULT_DISPLAY_NAME = "超级管理员";

    /** 允许默认密码继续存活的 profile（仅本地开发） */
    private static final Set<String> DEV_PROFILES = new HashSet<>(Arrays.asList("dev", "local", "test"));

    /** 紧急逃生开关 — 仅在 admin 后台无法登录、需要先重置密码的灾备场景使用 */
    private static final String BYPASS_ENV_NAME = "ADMIN_ALLOW_DEFAULT_PASSWORD";

    private final AdminUserRepository adminUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    public void run(String... args) {
        ensureDefaultAdminExists();
        verifyDefaultPasswordChanged();
    }

    /** 表为空则插入默认超管账号 */
    private void ensureDefaultAdminExists() {
        if (adminUserRepository.count() > 0) {
            log.info("admin_user 表已存在数据，跳过默认管理员初始化");
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setUsername(DEFAULT_USERNAME);
        admin.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        admin.setDisplayName(DEFAULT_DISPLAY_NAME);
        admin.setRole(AdminRole.SUPER_ADMIN);
        admin.setEnabled(true);
        adminUserRepository.save(admin);
        log.warn("已创建默认超级管理员账号 username={} password={}（请立即登录后台修改密码！）",
                DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    /**
     * 启动期强校验：若默认账号仍使用默认密码，且当前不是 dev profile，则拒启动。
     * <p>开发场景下仍允许 admin/admin123 直接登录，方便本地调试。
     */
    private void verifyDefaultPasswordChanged() {
        AdminUser admin = adminUserRepository.findByUsername(DEFAULT_USERNAME).orElse(null);
        if (admin == null) {
            // 默认账号已被管理员删除 — 视为安全
            return;
        }
        if (!passwordEncoder.matches(DEFAULT_PASSWORD, admin.getPasswordHash())) {
            // 已修改 — OK
            return;
        }

        boolean devProfile = isDevProfile();
        boolean bypass = "true".equalsIgnoreCase(System.getenv(BYPASS_ENV_NAME));

        if (devProfile) {
            log.warn("⚠️ [DEV] 检测到默认管理员 {} 仍使用默认密码 admin123，仅在 dev profile 下允许，上线前必改！",
                    DEFAULT_USERNAME);
            return;
        }

        log.error("========================================================================");
        log.error("⚠️  严重安全风险：默认管理员账号 ({}) 仍在使用默认密码 admin123！", DEFAULT_USERNAME);
        log.error("    当前 profile: {}", Arrays.toString(environment.getActiveProfiles()));
        log.error("    请立即用默认账号登录后台修改密码后再启动服务。");
        log.error("    如确需在生产临时跳过此检查（不推荐），请设置环境变量 {}=true", BYPASS_ENV_NAME);
        log.error("========================================================================");

        if (bypass) {
            log.error("⚠️ 检测到 {}=true，跳过安全校验继续启动 — 这是高危操作，请尽快修改默认密码！",
                    BYPASS_ENV_NAME);
            return;
        }

        throw new IllegalStateException(
                "默认管理员密码未修改，已拒绝启动（profile=" + Arrays.toString(environment.getActiveProfiles())
                        + "）。请先用 admin/admin123 登录后台改密，或临时设置 " + BYPASS_ENV_NAME + "=true 跳过。"
        );
    }

    private boolean isDevProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            // 未显式指定 profile — 保守按"非 dev"处理，强制走校验
            return false;
        }
        for (String p : active) {
            if (DEV_PROFILES.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
