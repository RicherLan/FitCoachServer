package com.lanprojects.fitcoach.common.client;

/**
 * 客户端版本信息 — 由 {@link ClientInfoInterceptor} 从请求 Header 解析得到，
 * 通过 {@link ClientContext} 暴露给业务层。
 *
 * <p><b>两层版本设计</b>（与 RN 端 src/common/clientInfo/clientInfo.ts 完全对齐）：
 * <ul>
 *   <li><b>nativeVersion</b>：安装包版本（用户在应用市场看到的）。
 *       改动场景：加 native 依赖、改原生 Module（PoseModule / Camera / Logger 等）。
 *       业务用途：判断"用户的 App 安装包是否支持某个新 native 接口"。</li>
 *   <li><b>bundleVersion</b>：当前运行的 JS bundle 版本，来源是 RN 项目 package.json 的 version。
 *       改动场景：纯 JS / TS / TSX 改动。未来接 OTA 后可静默推送（无需用户升级 App）。
 *       业务用途：判断"当前运行的代码是否实现了某个新业务逻辑/契约"。</li>
 *   <li><b>deviceId</b>：设备唯一标识。RN 客户端首次安装生成 UUIDv4 持久化到 AsyncStorage，
 *       后续启动复用。卸载重装会得到新 ID（这是预期：不依赖 IMEI/Android-ID/IDFA 避免隐私敏感字段）。
 *       业务用途：单设备登录互踢、按设备维度审计、风控。</li>
 *   <li><b>lang</b>：客户端当前界面语言（BCP-47），如 "zh-CN" / "en" / "fr" / "ja"。
 *       由 RN 端 i18n store 决定（用户手动切换 &gt; 系统语言 &gt; 默认 zh-CN），逐请求上报。
 *       业务用途：错误提示 / toast / 邮件等"对客户端可见的文案"按此语言翻译后下发。
 *       缺失/不识别时回落到 zh-CN（见 {@link ClientContext#locale()}）。</li>
 *   <li><b>appFlavor</b>：App 编译期市场标识（CN / GLOBAL / null）。与 RN 端
 *       {@code src/common/flavor/flavor.ts} 的 {@code getAppFlavor()} 完全对齐。
 *       业务用途：登录方式合规校验（如 CN 包不允许 Google 登录）、注册来源统计、按市场分维度分析。
 *       缺失场景：admin 后台 / Postman / 未升级到阶段 2 契约的老客户端。</li>
 * </ul>
 *
 * <p><b>编码规则</b>：versionCode = MAJOR*1_000_000 + MINOR*1_000 + PATCH（每段 0-999）。
 * 例 "1.2.3" → 1_002_003。便于做 {@code if (clientVersionCode >= 1_002_000)} 这种判断。
 *
 * <p><b>缺失语义</b>：
 * <ul>
 *   <li>非 RN 客户端调用（如 admin 后台、Postman 调试、未来的微信小程序 SDK），
 *       Header 缺失时 platform=null、versionCode=0、versionName=null、deviceId=null、appFlavor=null，业务侧自己判 null/0。</li>
 *   <li>解析失败（Header 存在但格式异常）也按缺失处理，避免脏数据。</li>
 *   <li>客户端启动早期 deviceIdProvider 未就绪窗口里 Header 值为 "unknown"，
 *       业务侧应同时把 null 与 "unknown" 当作"未知设备"处理（用 {@link #hasDeviceId()}）。</li>
 * </ul>
 */
public record ClientVersionInfo(
        String platform,           // "android" / "ios" / null
        int nativeVersionCode,     // 0 = unknown
        String nativeVersionName,  // "1.2.3" / null
        int bundleVersionCode,     // 0 = unknown
        String bundleVersionName,  // "1.2.3" / null
        String deviceId,           // RN 端 UUIDv4 / "unknown" / null
        String lang,               // BCP-47 语言标签，如 "zh-CN" / "en" / null
        AppFlavor appFlavor        // CN / GLOBAL / null（非 RN 客户端或老版本）
) {

    /** 客户端启动早期 deviceIdProvider 未就绪窗口里上报的占位值，与 RN 端常量保持一致 */
    public static final String UNKNOWN_DEVICE_ID = "unknown";

    public static final ClientVersionInfo EMPTY = new ClientVersionInfo(null, 0, null, 0, null, null, null, null);

    /** 是否完全没有客户端信息（admin 后台 / Postman / 老版本无埋点客户端 都会落到这里） */
    public boolean isEmpty() {
        return platform == null && nativeVersionCode == 0 && bundleVersionCode == 0;
    }

    /**
     * 是否拿到了真实有效的 deviceId（非 null、非 "unknown"、非空白）。
     * <p>互踢 / 风控等业务在 false 时应当跳过（避免把"未知设备"当成"另一台设备"误踢）。
     */
    public boolean hasDeviceId() {
        return deviceId != null
                && !deviceId.isBlank()
                && !UNKNOWN_DEVICE_ID.equals(deviceId);
    }

    /**
     * 是否上报了合法 flavor（CN 或 GLOBAL）。
     * <p>用于业务判断"这是不是一个已升级到阶段 2 契约的 RN 客户端"，
     * 未升级的老客户端 / admin / Postman 都返回 false，业务侧可跳过 flavor 相关校验。
     */
    public boolean hasAppFlavor() {
        return appFlavor != null;
    }

    /** 是否为 Android 端 */
    public boolean isAndroid() {
        return "android".equals(platform);
    }

    /** 是否为 iOS 端 */
    public boolean isIos() {
        return "ios".equals(platform);
    }
}
