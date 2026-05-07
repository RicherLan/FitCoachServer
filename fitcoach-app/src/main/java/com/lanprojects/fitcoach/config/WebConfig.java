package com.lanprojects.fitcoach.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置 — CORS 跨域等
 * <p>
 * 显式列出 allowedHeaders / exposedHeaders，避免 RN/Web 端某些预检请求被默拒。
 * 生产环境建议把 allowedOriginPatterns 收紧到具体域名，而不是 "*"。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders(
                        "Content-Type",
                        "Authorization",
                        "Accept",
                        "Accept-Language",
                        "Cache-Control",
                        "X-Requested-With"
                )
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
