package com.lanprojects.fitcoach.common.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 解析 —— 优先取反向代理透传头，避免拿到所有请求都是 LB / 容器网关的内网 IP。
 *
 * <p>解析顺序：
 * <ol>
 *   <li>{@code X-Forwarded-For} —— 多 IP 时取第一个（客户端真实 IP）；</li>
 *   <li>{@code X-Real-IP} —— Nginx 等常用透传头；</li>
 *   <li>{@link HttpServletRequest#getRemoteAddr()} —— 直连兜底。</li>
 * </ol>
 *
 * <p><b>安全提醒</b>：生产环境必须确保最外层是可信代理（如 Nginx），且代理会清洗 / 覆写
 * 客户端伪造的 {@code X-Forwarded-For}；否则该头可被任意伪造，会绕过 IP 维度限频 / 风控。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    /**
     * 解析客户端真实 IP；request 为 null 时返回 null。
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            return (comma > 0 ? ip.substring(0, comma) : ip).trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
