package com.lanprojects.fitcoach.config;

import com.lanprojects.fitcoach.common.client.ClientInfoInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.io.File;

/**
 * Web 配置 — CORS 跨域 + 静态资源映射 + 客户端版本拦截器注册
 * <p>
 * - CORS：显式列出 allowedHeaders / exposedHeaders，避免 RN/Web 端某些预检请求被默拒。
 *   生产环境建议把 allowedOriginPatterns 收紧到具体域名，而不是 "*"。
 * - 静态资源：把 {@code upload.base-dir} 映射到 {@code /static/**}，
 *   头像等上传文件可通过 {@code http://host:port/static/avatar/xxx.jpg} 访问。
 *   后期切 OSS 时把 {@code upload.url-prefix} 改成 OSS 域名即可，前端无感。
 * - 客户端版本拦截器：把请求头里的 {@code X-Client-*} 五件套解析到
 *   {@link com.lanprojects.fitcoach.common.client.ClientContext}，业务侧可静态读。
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 上传文件存盘根目录（绝对/相对都支持，相对路径以启动目录为基准） */
    @Value("${upload.base-dir}")
    private String uploadBaseDir;

    private final ClientInfoInterceptor clientInfoInterceptor;

    public WebConfig(ClientInfoInterceptor clientInfoInterceptor) {
        this.clientInfoInterceptor = clientInfoInterceptor;
    }

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
                        "X-Requested-With",
                        // 客户端版本/设备六件套（与 RN httpClient.ts / ClientInfoInterceptor 严格对齐）
                        ClientInfoInterceptor.HDR_PLATFORM,
                        ClientInfoInterceptor.HDR_NATIVE_VERSION_CODE,
                        ClientInfoInterceptor.HDR_NATIVE_VERSION_NAME,
                        ClientInfoInterceptor.HDR_BUNDLE_VERSION_CODE,
                        ClientInfoInterceptor.HDR_BUNDLE_VERSION_NAME,
                        ClientInfoInterceptor.HDR_DEVICE_ID
                )
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 拦截器注册 — 客户端版本信息收口。
     * <p>覆盖所有 {@code /api/**}（含未来新增的业务接口），
     * 排除 {@code /api/admin/**}（管理后台不发版客户端 header，注册了也是 EMPTY 没意义，
     * 反而让 admin 的 {@link com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor}
     * 多走一段无用代码）。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(clientInfoInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/**");
    }

    /**
     * 静态资源映射：
     * <ul>
     *   <li>{@code /static/**} → 上传目录（用户上传的头像等动态文件）</li>
     *   <li>{@code /assets/**} → classpath 下的 {@code static-assets/}（默认头像、占位图等随包内置资源）</li>
     * </ul>
     * 把"内置资源"和"用户上传"拆成两个 URL 前缀，互不干扰：
     *   - 用户上传的头像永远不会覆盖内置默认头像
     *   - 升级 / 修改默认头像只需要重新发包，不影响磁盘上的用户数据
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File dir = new File(uploadBaseDir);
        // 启动时若目录不存在主动创建一次，省得运维忘记 mkdir 导致首次上传 500
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("无法创建上传目录: {}", dir.getAbsolutePath());
        }
        String uploadLocation = "file:" + dir.getAbsolutePath() + File.separator;
        log.info("静态资源映射: /static/** -> {}", uploadLocation);
        registry.addResourceHandler("/static/**")
                .addResourceLocations(uploadLocation);

        // 内置资源映射 — classpath:/static-assets/ 下的资源（如 default-avatar.svg）
        log.info("静态资源映射: /assets/** -> classpath:/static-assets/");
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static-assets/");
    }

    @PostConstruct
    public void logUploadDir() {
        log.info("upload.base-dir = {}", new File(uploadBaseDir).getAbsolutePath());
    }
}
