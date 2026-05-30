package com.lanprojects.fitcoach.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) 文档配置。
 * <p>
 * 仅当 {@code springdoc.api-docs.enabled=true}（默认 true）时才装配，
 * 在生产环境 yml 中显式设为 false 即可整体关闭文档暴露。
 *
 * <h3>访问入口</h3>
 * <ul>
 *   <li>Swagger UI 总入口：{@code http://host:port/swagger-ui.html} （会自动跳到 /swagger-ui/index.html）</li>
 *   <li>App 客户端接口分组：{@code /v3/api-docs/client}</li>
 *   <li>Admin 后台接口分组：{@code /v3/api-docs/admin}</li>
 * </ul>
 *
 * <h3>分组策略</h3>
 * <p>用 {@link GroupedOpenApi} 把全部接口按 URL 前缀切成两组：
 * <ul>
 *   <li><b>client</b>：所有 {@code /api/**} 但排除 {@code /api/admin/**}，给 App 端开发者看。</li>
 *   <li><b>admin</b>：仅 {@code /api/admin/**}，给后台前端 / 内部工具看。</li>
 * </ul>
 * 两组共享同一份全局 Info / Server / Security 配置（在 {@link #fitcoachOpenAPI()} 定义）。
 *
 * <h3>鉴权说明</h3>
 * <p>声明了一个名为 {@code bearer-jwt} 的 SecurityScheme，全局应用。
 * 在 Swagger UI 右上角 "Authorize" 按钮里填入 JWT（不含 "Bearer " 前缀），
 * 此后所有"Try it out"都会自动带上 {@code Authorization: Bearer <token>} 头。
 *
 * <h3>Controller 写文档的最小注解集合</h3>
 * <pre>
 *   {@literal @}Tag(name = "用户管理")
 *   {@literal @}RestController
 *   public class UserController {
 *       {@literal @}Operation(summary = "获取用户列表", description = "支持分页和关键字搜索")
 *       {@literal @}GetMapping("/users")
 *       public Result&lt;Page&lt;User&gt;&gt; list(...) { ... }
 *   }
 * </pre>
 * 不加注解的 controller 也会被收录，只是没有友好的中文标题。
 */
@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

    /** 全局 JWT 鉴权方案名称（与 @SecurityRequirement(name=...) 对应） */
    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

    /**
     * 全局 OpenAPI 定义：项目元信息 + 鉴权方案 + 服务器列表。
     */
    @Bean
    public OpenAPI fitcoachOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FitCoach Server API")
                        .description("FitCoach AI 健身教练 — 后端 REST API 文档。\n\n"
                                + "**鉴权**：除登录 / OTP / Ping 等公开接口外，所有接口都需要在 Header 携带 "
                                + "`Authorization: Bearer <jwt>`。点击右上角 `Authorize` 一次填入即可。\n\n"
                                + "**统一响应**：所有接口返回 `Result<T>` 包装结构 — `{ code, message, data }`，"
                                + "其中 `code=0` 代表成功。")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("FitCoach Team")
                                .email("admin@migofitai.com"))
                        .license(new License()
                                .name("Proprietary")))
                .servers(List.of(
                        new Server().url("/").description("当前服务（相对路径，跟随域名）")
                ))
                // 全局加 security requirement，所有接口默认要求 JWT；
                // 公开接口（登录、ping、OTP 发送等）可在 controller 方法上用
                // @io.swagger.v3.oas.annotations.security.SecurityRequirements 清空。
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT 鉴权 — 登录后从响应取 token，填入此处（不带 \"Bearer \" 前缀）")));
    }

    /**
     * Admin 后台接口分组 —— 仅 {@code /api/admin/**}。
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("Admin 后台")
                .pathsToMatch("/api/admin/**")
                .build();
    }

    /**
     * App 客户端接口分组 —— 所有 {@code /api/**} 但排除 admin。
     * <p>排除写在 pathsToExclude 而不是用反向正则，更直观也更稳。
     */
    @Bean
    public GroupedOpenApi clientApi() {
        return GroupedOpenApi.builder()
                .group("client")
                .displayName("App 客户端")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/admin/**")
                .build();
    }
}
