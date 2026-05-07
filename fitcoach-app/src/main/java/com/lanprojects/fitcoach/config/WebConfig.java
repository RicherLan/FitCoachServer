package com.lanprojects.fitcoach.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.io.File;

/**
 * Web 配置 — CORS 跨域 + 静态资源映射
 * <p>
 * - CORS：显式列出 allowedHeaders / exposedHeaders，避免 RN/Web 端某些预检请求被默拒。
 *   生产环境建议把 allowedOriginPatterns 收紧到具体域名，而不是 "*"。
 * - 静态资源：把 {@code upload.base-dir} 映射到 {@code /static/**}，
 *   头像等上传文件可通过 {@code http://host:port/static/avatar/xxx.jpg} 访问。
 *   后期切 OSS 时把 {@code upload.url-prefix} 改成 OSS 域名即可，前端无感。
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 上传文件存盘根目录（绝对/相对都支持，相对路径以启动目录为基准） */
    @Value("${upload.base-dir}")
    private String uploadBaseDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                // PATCH 也加上 — 用户资料的局部更新走 PATCH
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
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

    /**
     * 把上传目录映射成可被 HTTP 访问的静态资源。
     * <p>必须以 {@code /} 结尾，否则 Spring 会把最后一段当成文件名。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File dir = new File(uploadBaseDir);
        // 启动时若目录不存在主动创建一次，省得运维忘记 mkdir 导致首次上传 500
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("无法创建上传目录: {}", dir.getAbsolutePath());
        }
        String location = "file:" + dir.getAbsolutePath() + File.separator;
        log.info("静态资源映射: /static/** -> {}", location);
        registry.addResourceHandler("/static/**")
                .addResourceLocations(location);
    }

    @PostConstruct
    public void logUploadDir() {
        log.info("upload.base-dir = {}", new File(uploadBaseDir).getAbsolutePath());
    }
}
