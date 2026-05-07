package com.lanprojects.fitcoach.admin.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Admin 模块的 WebMvc 配置 — 注册 {@link AdminAuthInterceptor}。
 * <p>
 * - addPathPatterns：所有 {@code /api/admin/**} 接口默认要登录；
 * - excludePathPatterns：登录接口、健康检查必须放行，否则鸡生蛋蛋生鸡。
 * <p>
 * 该配置类与客户端的 {@code WebConfig} 互不影响，Spring 会把多个
 * {@code WebMvcConfigurer} 自动合并应用。
 */
@Configuration
@RequiredArgsConstructor
public class AdminWebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns(
                        "/api/admin/auth/login",
                        "/api/admin/ping"
                );
    }
}
