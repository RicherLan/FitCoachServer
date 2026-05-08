package com.lanprojects.fitcoach.common.client;

/**
 * 客户端上下文 — ThreadLocal 持有当前请求的 {@link ClientVersionInfo}，
 * 业务任意位置可通过静态方法读取，无需把 HttpServletRequest 一路下传。
 *
 * <p><b>生命周期</b>：
 * <ul>
 *   <li>由 {@link ClientInfoInterceptor#preHandle} 在请求开始时 set；</li>
 *   <li>由 {@link ClientInfoInterceptor#afterCompletion} 在请求结束时 clear（防止线程池复用时的脏读）。</li>
 * </ul>
 *
 * <p><b>使用示例</b>：
 * <pre>{@code
 * // 业务里判断"客户端是否到了支持密码登录的版本"：
 * if (ClientContext.bundleVersionCode() >= 1_001_000) {
 *     // 可以走新逻辑
 * }
 *
 * // 或直接拿全量信息：
 * ClientVersionInfo info = ClientContext.get();
 * if (info.isIos()) { ... }
 * }</pre>
 *
 * <p><b>异步场景注意</b>：ThreadLocal 不会自动跨线程传递，
 * 如果业务把请求处理 dispatch 到线程池（如 @Async），需手动捕获 {@link #get()} 后再传过去。
 */
public final class ClientContext {

    private static final ThreadLocal<ClientVersionInfo> HOLDER = new ThreadLocal<>();

    private ClientContext() {
        // 工具类禁止实例化
    }

    /** 仅 {@link ClientInfoInterceptor} 调用 */
    static void set(ClientVersionInfo info) {
        HOLDER.set(info);
    }

    /** 仅 {@link ClientInfoInterceptor} 调用 */
    static void clear() {
        HOLDER.remove();
    }

    /**
     * 获取当前请求的客户端信息。
     * <p>非 RN 客户端（admin/Postman/未来其他 SDK）请求时返回 {@link ClientVersionInfo#EMPTY}，
     * 业务侧不需要判 null。
     */
    public static ClientVersionInfo get() {
        ClientVersionInfo v = HOLDER.get();
        return v != null ? v : ClientVersionInfo.EMPTY;
    }

    // ====== 便捷 getter（最常用，省得每次 .get().xxx()） ======

    public static String platform() {
        return get().platform();
    }

    public static int nativeVersionCode() {
        return get().nativeVersionCode();
    }

    public static int bundleVersionCode() {
        return get().bundleVersionCode();
    }

    public static boolean isAndroid() {
        return get().isAndroid();
    }

    public static boolean isIos() {
        return get().isIos();
    }

    /**
     * 当前请求的设备唯一标识。
     * <p>RN 端首次安装生成 UUIDv4 持久化，后续启动复用。
     * <p>缺失时返回 null（admin 后台、Postman 调试），客户端早期未就绪窗口返回 "unknown"。
     * <p>需要严格判断"是否拿到真实 deviceId"（如单设备登录互踢、风控）请用
     * {@code ClientContext.get().hasDeviceId()}。
     */
    public static String deviceId() {
        return get().deviceId();
    }
}
