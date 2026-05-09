package com.lanprojects.fitcoach.common.client;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 客户端版本信息拦截器 — 解析 {@code X-Client-*} / {@code X-Device-Id} Header → {@link ClientContext}。
 *
 * <p><b>跨端协议（Header 六件套）</b>：
 * <ul>
 *   <li>{@link #HDR_PLATFORM}            — "android" / "ios"</li>
 *   <li>{@link #HDR_NATIVE_VERSION_CODE} — int，如 1002003（即 1.2.3 编码后）</li>
 *   <li>{@link #HDR_NATIVE_VERSION_NAME} — string，如 "1.2.3"</li>
 *   <li>{@link #HDR_BUNDLE_VERSION_CODE} — int，OTA 后会与 native 不同</li>
 *   <li>{@link #HDR_BUNDLE_VERSION_NAME} — string</li>
 *   <li>{@link #HDR_DEVICE_ID}           — string，RN 端 UUIDv4 / "unknown" / 缺失</li>
 *   <li>{@link #HDR_LANG}                — string，BCP-47 语言标签（"zh-CN" / "en" / "fr" / ...），用于 i18n</li>
 * </ul>
 *
 * <p><b>缺失/格式错误兜底</b>：
 * <ul>
 *   <li>Header 不存在 → 对应字段为 null/0；</li>
 *   <li>Header 存在但解析失败 → 静默降级为 0，不打 ERROR 日志（防止 admin 后台/Postman 调试刷屏）。</li>
 * </ul>
 *
 * <p><b>注册位置</b>：
 * <ul>
 *   <li>客户端业务接口 — {@code fitcoach-app/.../config/WebConfig} 注册到 {@code /api/**}，
 *       排除 {@code /api/admin/**}。</li>
 *   <li>admin 后台接口 — 不注册（admin 不发版客户端 header，注册了也是 EMPTY 没意义）。</li>
 * </ul>
 *
 * <p><b>必须 clear ThreadLocal</b>：Tomcat / Undertow 线程池复用，不 clear 会导致下个请求读到上个的脏数据。
 */
@Slf4j
@Component
public class ClientInfoInterceptor implements HandlerInterceptor {

    public static final String HDR_PLATFORM = "X-Client-Platform";
    public static final String HDR_NATIVE_VERSION_CODE = "X-Client-Native-Version-Code";
    public static final String HDR_NATIVE_VERSION_NAME = "X-Client-Native-Version-Name";
    public static final String HDR_BUNDLE_VERSION_CODE = "X-Client-Bundle-Version-Code";
    public static final String HDR_BUNDLE_VERSION_NAME = "X-Client-Bundle-Version-Name";
    public static final String HDR_DEVICE_ID = "X-Device-Id";
    /** 客户端当前界面语言（BCP-47），用于 i18n 翻译错误提示等对客户端可见的文案 */
    public static final String HDR_LANG = "X-Client-Lang";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String platform = trimToNull(request.getHeader(HDR_PLATFORM));
        int nativeCode = parseIntSafe(request.getHeader(HDR_NATIVE_VERSION_CODE));
        String nativeName = trimToNull(request.getHeader(HDR_NATIVE_VERSION_NAME));
        int bundleCode = parseIntSafe(request.getHeader(HDR_BUNDLE_VERSION_CODE));
        String bundleName = trimToNull(request.getHeader(HDR_BUNDLE_VERSION_NAME));
        String deviceId = trimToNull(request.getHeader(HDR_DEVICE_ID));
        String lang = trimToNull(request.getHeader(HDR_LANG));

        ClientVersionInfo info = new ClientVersionInfo(
                platform, nativeCode, nativeName, bundleCode, bundleName, deviceId, lang);
        ClientContext.set(info);

        // DEBUG 级日志：avoid 生产刷屏；排错时调到 DEBUG 即可看到每个请求的客户端信息
        if (log.isDebugEnabled() && !info.isEmpty()) {
            log.debug("client: platform={} native={}({}) bundle={}({}) device={} lang={} uri={}",
                    platform, nativeName, nativeCode, bundleName, bundleCode, deviceId, lang, request.getRequestURI());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 必须 clear，否则线程池复用会让下一个请求读到脏数据
        ClientContext.clear();
    }

    private static int parseIntSafe(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            int v = Integer.parseInt(s.trim());
            return v < 0 ? 0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
