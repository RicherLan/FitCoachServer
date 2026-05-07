package com.lanprojects.fitcoach.admin.config;

import com.lanprojects.fitcoach.admin.entity.AdminRole;
import com.lanprojects.fitcoach.admin.entity.AdminUser;
import com.lanprojects.fitcoach.admin.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 后台管理首次启动初始化器 — 写入默认超管账号。
 * <p>
 * 设计：
 * <ul>
 *   <li>只在 admin_user 表为空时插入，避免覆盖管理员后续修改的密码；</li>
 *   <li>默认账号：username={@value #DEFAULT_USERNAME} / password={@value #DEFAULT_PASSWORD}，
 *       <b>生产环境必须立即登录后修改</b>；</li>
 *   <li>角色固定为 SUPER_ADMIN，方便后续创建其他管理员。</li>
 * </ul>
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

    private final AdminUserRepository adminUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
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
}
