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
 * </ul>
 *
 * <p><b>编码规则</b>：versionCode = MAJOR*1_000_000 + MINOR*1_000 + PATCH（每段 0-999）。
 * 例 "1.2.3" → 1_002_003。便于做 {@code if (clientVersionCode >= 1_002_000)} 这种判断。
 *
 * <p><b>缺失语义</b>：
 * <ul>
 *   <li>非 RN 客户端调用（如 admin 后台、Postman 调试、未来的微信小程序 SDK），
 *       Header 缺失时 platform=null、versionCode=0、versionName=null，业务侧自己判 null/0。</li>
 *   <li>解析失败（Header 存在但格式异常）也按缺失处理，避免脏数据。</li>
 * </ul>
 */
public record ClientVersionInfo(
        String platform,           // "android" / "ios" / null
        int nativeVersionCode,     // 0 = unknown
        String nativeVersionName,  // "1.2.3" / null
        int bundleVersionCode,     // 0 = unknown
        String bundleVersionName   // "1.2.3" / null
) {

    public static final ClientVersionInfo EMPTY = new ClientVersionInfo(null, 0, null, 0, null);

    /** 是否完全没有客户端信息（admin 后台 / Postman / 老版本无埋点客户端 都会落到这里） */
    public boolean isEmpty() {
        return platform == null && nativeVersionCode == 0 && bundleVersionCode == 0;
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
