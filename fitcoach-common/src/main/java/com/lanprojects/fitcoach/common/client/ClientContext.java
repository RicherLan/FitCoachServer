package com.lanprojects.fitcoach.common.client;

import java.util.Locale;

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

    /**
     * 当前请求的客户端界面语言（BCP-47 标签，如 "zh-CN" / "en" / "fr"）。
     * <p>缺失时返回 null（admin 后台 / Postman / 老版本未上报客户端）。
     * <p>需要"语言对应的 Locale 对象"做 i18n 时请用 {@link #locale()}，已自动兜底。
     */
    public static String lang() {
        return get().lang();
    }

    /**
     * 当前请求的客户端语言对应的 {@link Locale}，用于 Spring MessageSource 翻译。
     * <p><b>兜底链</b>：lang 为空 / 解析失败 → {@code Locale.SIMPLIFIED_CHINESE}（zh_CN）。
     * 选中文做兜底是因为本项目主要受众是中文用户，admin 后台 / 调试请求都期望看到中文。
     */
    public static Locale locale() {
        String tag = lang();
        if (tag == null || tag.isBlank()) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        try {
            Locale parsed = Locale.forLanguageTag(tag);
            // forLanguageTag 对完全无法解析的字符串返回 ROOT (空 Locale)，这里也按兜底处理
            if (parsed.getLanguage().isBlank()) {
                return Locale.SIMPLIFIED_CHINESE;
            }
            return parsed;
        } catch (Exception e) {
            return Locale.SIMPLIFIED_CHINESE;
        }
    }

    // ====== App Flavor 便捷 getter ======

    /**
     * 当前请求的 App 编译期市场标识（CN / GLOBAL / null）。
     * <p>缺失场景：admin 后台 / Postman / 未升级到阶段 2 契约的老客户端。
     * <p>业务侧不要在缺失时"假设 CN 或 GLOBAL"—— 缺失有明确语义（不是 RN 客户端），
     * 需要按 flavor 做分支的业务应在缺失时走"两边都不满足"的兜底分支。
     */
    public static AppFlavor appFlavor() {
        return get().appFlavor();
    }

    /**
     * 语法糖：当前请求是否来自国内包（CN flavor）。
     * <p>null 时返回 false（保守：非 RN 客户端不算 CN，避免把 admin 请求当 CN 处理）。
     */
    public static boolean isCn() {
        return get().appFlavor() == AppFlavor.CN;
    }

    /**
     * 语法糖：当前请求是否来自海外包（GLOBAL flavor）。
     * <p>null 时返回 false（保守：非 RN 客户端不算 GLOBAL）。
     */
    public static boolean isGlobal() {
        return get().appFlavor() == AppFlavor.GLOBAL;
    }
}
