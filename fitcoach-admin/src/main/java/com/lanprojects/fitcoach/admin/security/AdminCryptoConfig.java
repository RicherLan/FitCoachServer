package com.lanprojects.fitcoach.admin.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 仅暴露一个 {@link BCryptPasswordEncoder} Bean 给 admin 模块用，
 * 不引整套 Spring Security 的 Filter Chain，避免对客户端接口产生影响。
 * <p>strength=10（默认 10 即 2^10 轮，约 100ms/次，足够抗暴力且对 admin 登录无感）。
 */
@Configuration
public class AdminCryptoConfig {

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
