package com.lanprojects.fitcoach.login.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 全局唯一的 {@link BCryptPasswordEncoder} Bean。
 * <p>放在 fitcoach-login 模块下让所有用到密码哈希的下游模块共用同一份策略：
 * <ul>
 *     <li>fitcoach-login —— 客户端用户密码登录 / 设置密码</li>
 *     <li>fitcoach-admin —— 管理员账号密码登录 / 改密（depends on fitcoach-login）</li>
 * </ul>
 * <p>strength=10：默认 10 即 2^10 轮，约 100ms/次，足够抗暴力且对登录交互无感。
 * 注意：升级 strength 时无需迁移历史数据 —— BCrypt 哈希字符串本身记录了 cost，
 * 校验时会按存储的 cost 计算，新写入用新 cost 即可。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
