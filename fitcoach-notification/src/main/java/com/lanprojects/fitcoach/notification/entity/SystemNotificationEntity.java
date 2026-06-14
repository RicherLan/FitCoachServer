package com.lanprojects.fitcoach.notification.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统通知（站内弹窗）记录。
 *
 * <p><b>用途</b>：admin 后台为某次"运营公告 / 重要提醒 / 单点通知"创建一条记录，
 * 客户端通过 {@code /api/client/poll} 拉到后用全局 AppDialog 弹给用户。
 *
 * <p><b>"轮询拉取"语义</b>（与 push 推送的根本区别）：
 * <ul>
 *   <li>客户端用本地 seenIds 去重 —— 同一条通知只弹一次；</li>
 *   <li>卸载重装 / 清理 app 数据后 seenIds 丢失，用户可能再看一次（用户已同意此行为）；</li>
 *   <li>server 不感知"哪个用户看过哪条" —— 不建 ack 表，少一张表少一类维护成本。</li>
 * </ul>
 *
 * <p><b>有效期</b>：admin 创建时选择"最多存在 N 天"，service 层换算成 {@code expire_at = now + N*86400000}。
 * 客户端 poll 查询时按 {@code effective_at &lt;= now &lt; expire_at} 过滤；
 * 过期后 server 永远不会再下发，但 entity 不会被自动删除（admin 仍可查历史）。
 *
 * <p><b>"一次返回当前所有可见通知"语义</b>：客户端 poll 响应里返回 List；
 * 服务端按 created_at DESC 排序，客户端按队列依次弹出（关一个再弹下一个），
 * 整体上限由服务端常量 {@code SystemNotificationService#MAX_POLL_RETURN} 控制，
 * 避免极端情况下一次塞回几十条把客户端压垮。
 *
 * <p><b>按钮跳转</b>：{@code primaryButtonUrl} / {@code secondaryButtonUrl} 是 deeplink，
 * 由客户端 deeplinkRouter 解析（如 {@code migofit://web?url=https://...}）；空字符串表示"仅关闭弹窗"。
 *
 * <p><b>平台 + 版本过滤</b>（v1.1 新增）：
 * <ul>
 *   <li>{@link #platforms} —— CSV，如 {@code "android"} / {@code "android,ios"}；null/空 = 不限平台；</li>
 *   <li>{@link #minVersionCode} —— 客户端 versionCode &gt;= 此值才命中；null = 不限下限；</li>
 *   <li>{@link #maxVersionCode} —— 客户端 versionCode &lt;= 此值才命中；null = 不限上限；</li>
 *   <li>三个字段都为空 = 全平台全版本；服务端 + 客户端共用一个 {@link com.lanprojects.fitcoach.common.client.ClientContext} 拿真实平台/版本。</li>
 * </ul>
 * 版本号编码与 {@code fitcoach-appversion} 模块对齐：MAJOR*1_000_000 + MINOR*1_000 + PATCH，
 * 例 {@code "1.2.3" → 1002003}；int 比较，无 SemVer 字符串解析歧义。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "system_notification",
        indexes = {
                // poll 查询热路径：status + effective_at + expire_at + created_at 都参与
                @Index(name = "idx_sysnotif_status_window_created",
                        columnList = "status, effective_at, expire_at, created_at"),
        })
public class SystemNotificationEntity extends BaseEntity {

    /** 弹窗标题（必填，最长 100 字符），客户端直接展示，无 i18n 翻译 */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /** 弹窗正文（必填，最长 1000 字符），支持 \n 换行；客户端用 Text 渲染，不做富文本 */
    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    // ====== 主按钮（必填） ======

    /** 主按钮文案（必填，最长 30 字符），如「立即查看」「我知道了」 */
    @Column(name = "primary_button_text", nullable = false, length = 30)
    private String primaryButtonText;

    /**
     * 主按钮点击跳转 URL（可空字符串表示"仅关闭弹窗"，非 null）。
     * <p>支持：
     * <ul>
     *   <li>内部路由：{@code migofit://screen/Training?exerciseKey=squat}</li>
     *   <li>内嵌 WebView：{@code migofit://web?url=https%3A%2F%2Fexample.com%2Fpost%2F1}</li>
     *   <li>外部浏览器：{@code https://example.com}（客户端会用 Linking.openURL）</li>
     * </ul>
     */
    @Column(name = "primary_button_url", nullable = false, length = 1000)
    private String primaryButtonUrl = "";

    // ====== 副按钮（可空，null 表示不显示该按钮） ======

    /** 副按钮文案（可空，最长 30 字符）；null 时客户端只渲染主按钮 */
    @Column(name = "secondary_button_text", length = 30)
    private String secondaryButtonText;

    /** 副按钮 URL（可空），语义同 {@link #primaryButtonUrl} */
    @Column(name = "secondary_button_url", length = 1000)
    private String secondaryButtonUrl;

    // ====== 投放对象 ======

    /** 投放对象类型：ALL（所有人）/ SPECIFIC_USERS（仅 target_uids 列表内） */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_audience", nullable = false, length = 32)
    private TargetAudience targetAudience = TargetAudience.ALL;

    /**
     * 指定的 uid 列表（仅 {@code targetAudience = SPECIFIC_USERS} 时有效），
     * 以英文逗号分隔的字符串存储，如 {@code "uid_a,uid_b,uid_c"}。
     * <p>不用 JSON / 不建独立关系表：本期一条通知最多指定数十个 uid 的场景，
     * 用 LIKE / 内存 contains 已足够；后续若需"针对万级用户"再考虑独立表。
     * <p>{@code targetAudience = ALL} 时此字段应为 null。
     */
    @Column(name = "target_uids", length = 4000)
    private String targetUids;

    // ====== 时间窗口 ======

    /**
     * 生效时间。
     * <p>等于 {@code createdAt} 时表示"立即生效"；admin 也可设未来时间做"定时上架"。
     * <p>客户端 poll 时按 {@code now &gt;= effective_at} 过滤。
     */
    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    /**
     * 过期时间。
     * <p>由 admin 选择"存在最多 N 天"在 service 层换算：{@code expire_at = effective_at + N 天}。
     * <p>客户端 poll 时按 {@code now &lt; expire_at} 过滤；过期后永远拉不到，
     * 但记录不会被自动删除（admin 可查历史 + 复用文案）。
     */
    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

    /** 发布状态：DRAFT / PUBLISHED / ARCHIVED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NotificationStatus status = NotificationStatus.DRAFT;

    // ====== 平台 + 版本过滤（v1.1 新增） ======

    /**
     * 投放平台 CSV，与 {@link com.lanprojects.fitcoach.common.client.ClientVersionInfo#platform()} 取值一致。
     * <p>合法元素：{@code "android"} / {@code "ios"}（小写）；多平台逗号分隔，如 {@code "android,ios"}。
     * <p>null / 空字符串 = 不限平台（全平台命中）。
     * <p>本字段在 service 层用 {@code String.split(",") + contains(platform)} 精确判定，
     * 不依赖 DB 的 LIKE 匹配 —— 一条记录最多 2-3 个平台值，内存判定开销可忽略。
     */
    @Column(name = "platforms", length = 64)
    private String platforms;

    /**
     * 命中下限：客户端 {@code nativeVersionCode >= minVersionCode} 才命中。
     * <p>null = 不限下限（全版本命中）。
     * <p>版本号编码：MAJOR*1_000_000 + MINOR*1_000 + PATCH，例 "1.2.3" → 1002003，与
     * {@code fitcoach-appversion} 模块的 {@code AppVersionEntity#versionCode} 一致。
     * <p>典型场景："这个新功能只在 1.5.0 及以上提示" → minVersionCode=1005000，max 留空。
     */
    @Column(name = "min_version_code")
    private Integer minVersionCode;

    /**
     * 命中上限：客户端 {@code nativeVersionCode <= maxVersionCode} 才命中。
     * <p>null = 不限上限（全版本命中）。
     * <p>典型场景："给老版本推升级提醒" → max 设当前最高发布版本，新版本不会再看到此提示。
     */
    @Column(name = "max_version_code")
    private Integer maxVersionCode;

    // ====== 审计 ======

    /** 创建者（admin 用户名），用于审计；可空（migrate 脚本/seeder 创建时为 null） */
    @Column(name = "created_by", length = 64)
    private String createdBy;
}
