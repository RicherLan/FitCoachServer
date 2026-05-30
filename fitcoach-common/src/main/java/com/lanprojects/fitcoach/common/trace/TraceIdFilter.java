package com.lanprojects.fitcoach.common.trace;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.util.UUID;

/**
 * 在请求入口为每条请求绑定一个 traceId（透传 + 落 MDC）。
 *
 * <p><b>行为</b>：
 * <ul>
 *   <li>读 request header {@code X-Trace-Id}（前端 / 上游可主动传入做端到端串联）；</li>
 *   <li>没有就生成一个 16 位 hex 短 UUID；</li>
 *   <li>放入 SLF4J {@link MDC}（key={@link #MDC_KEY}），日志 pattern 用 {@code %X{traceId}} 输出；</li>
 *   <li>响应回写 {@code X-Trace-Id} header，便于客户端把它一起回传给问题反馈接口；</li>
 *   <li>{@code finally} 清 MDC，避免线程复用造成串号。</li>
 * </ul>
 *
 * <p><b>顺序</b>：用 {@link FilterRegistrationBean} 注册到最高优先级，
 * 保证所有其它过滤器 / 拦截器的日志都能带上 traceId。
 */
@Configuration
public class TraceIdFilter {

    /** MDC key，也是 Logback pattern / {@link com.lanprojects.fitcoach.common.model.Result} 取值的 key */
    public static final String MDC_KEY = "traceId";

    /** HTTP header 名，端到端串联用 */
    public static final String HEADER_NAME = "X-Trace-Id";

    @Bean
    public FilterRegistrationBean<Filter> traceIdFilterRegistration() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new TraceIdServletFilter());
        reg.addUrlPatterns("/*");
        reg.setName("traceIdFilter");
        // 最高优先级 — 保证后续 filter / interceptor 日志都能带上 traceId
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    /**
     * 真正干活的 servlet filter — 内部类避免被 component scan 重复注册。
     */
    static class TraceIdServletFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse resp = (HttpServletResponse) response;

            String traceId = req.getHeader(HEADER_NAME);
            if (traceId == null || traceId.isBlank() || traceId.length() > 64) {
                // 16 位 hex（取 UUID 前半段）— 既短又有足够熵
                traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }

            MDC.put(MDC_KEY, traceId);
            resp.setHeader(HEADER_NAME, traceId);
            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove(MDC_KEY);
            }
        }
    }
}
