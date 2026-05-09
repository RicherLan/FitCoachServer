package com.lanprojects.fitcoach.appversion.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 客户端 App 版本记录（admin 后台维护，App 端「检查更新」时拉取）。
 *
 * <p><b>用途</b>：
 * <ul>
 *   <li>admin 在后台为每个新版本（android / ios 各自）创建一条记录，
 *       填写 versionName / versionCode / 更新说明 / 应用商店下载链接，发布后 App 端可见；</li>
 *   <li>App 端打开「关于 → 检查更新」时调 {@code GET /api/app/version/latest?platform=android}，
 *       与本地 package.json.version（= apk versionName）对比，决定是否弹升级窗。</li>
 * </ul>
 *
 * <p><b>"两版本同号"机制（与 RN 端 src/common/clientInfo/clientInfo.ts 对齐）</b>：
 * versionCode = MAJOR*1_000_000 + MINOR*1_000 + PATCH（每段 0-999），
 * 例 "1.2.3" → 1_002_003。比较大小用 versionCode（int 比较，准确无歧义），
 * 展示给用户用 versionName（"1.2.3" 字符串）。
 *
 * <p><b>幂等约束</b>：(platform, versionCode) 唯一 —— 同平台不允许出现两条同 versionCode 记录，
 * 避免"哪个才是最终发布"的歧义；如要替换，先删旧的或编辑现有的。
 *
 * <p><b>"已发布"语义</b>：{@code isPublished=false} 时该记录对 App 端不可见（admin 草稿态），
 * 用于运营提前录入但等应用商店审核通过后再正式上架。
 * App 端「最新版本」查询永远只看 {@code isPublished=true} 中 versionCode 最大的一条。
 *
 * <p><b>本期不做 i18n 化</b>：{@code releaseNotes} 单字段，admin 可按需写"中文行 + English 行"形式，
 * 简化录入；后续若海外用户量明显，再切成"按 lang 分行存储"或单独的 i18n 字典表。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_version",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_appversion_platform_code",
                        columnNames = {"platform", "version_code"})
        },
        indexes = {
                // App 端查询热路径：按平台过滤 + 按 versionCode 倒序找最新已发布
                @Index(name = "idx_appversion_platform_published_code",
                        columnList = "platform, is_published, version_code"),
                // Admin 列表常按平台过滤后按 versionCode 倒序展示
                @Index(name = "idx_appversion_platform_code",
                        columnList = "platform, version_code")
        })
public class AppVersionEntity extends BaseEntity {

    /**
     * 客户端平台，与 {@link com.lanprojects.fitcoach.common.client.ClientVersionInfo#platform()} 取值一致。
     * <p>当前合法取值：{@code "android"} / {@code "ios"}（小写）。其它取值由 service 层在写入前校验拒绝。
     */
    @Column(name = "platform", nullable = false, length = 16)
    private String platform;

    /**
     * 展示用版本号字符串，例 {@code "1.2.3"}。
     * <p>会出现在升级弹窗标题、release notes 头部，需与 apk/ipa 真实安装包版本完全一致。
     */
    @Column(name = "version_name", nullable = false, length = 32)
    private String versionName;

    /**
     * 数值化版本号，用于「比较大小」判定客户端是否需要升级（int 比较，无字符串比较歧义）。
     * <p>编码规则：MAJOR*1_000_000 + MINOR*1_000 + PATCH（每段 0-999）。
     * 例 "1.2.3" → 1002003。
     */
    @Column(name = "version_code", nullable = false)
    private Integer versionCode;

    /**
     * 更新说明（release notes），客户端「检查更新」弹窗 body 直接展示。
     * <p>支持简单换行（\n），admin 后台用 textarea 录入。本期不做富文本/markdown 渲染。
     */
    @Column(name = "release_notes", length = 2000)
    private String releaseNotes;

    /**
     * 应用商店跳转链接 —— 客户端点击「去更新」按钮时由 {@code Linking.openURL} 打开。
     * <ul>
     *   <li>Android 推荐填 {@code market://details?id=com.fitcoach.app}（可同时备一个 H5 页地址）；</li>
     *   <li>iOS 填 App Store URL：{@code https://apps.apple.com/app/idXXXXXXXXX} 或
     *       {@code itms-apps://itunes.apple.com/app/idXXXXXXXXX}。</li>
     * </ul>
     * <p>客户端跳转失败由客户端自行兜底（如 catch 后 toast 提示"请手动到应用商店搜索"）。
     */
    @Column(name = "download_url", nullable = false, length = 512)
    private String downloadUrl;

    /**
     * 是否强制升级。
     * <ul>
     *   <li>{@code true} ：弹窗只有「去更新」按钮，无「取消」（客户端 UI 实现，server 仅返回该 flag）；</li>
     *   <li>{@code false}：弹窗有「去更新」+「稍后」两个按钮（默认）。</li>
     * </ul>
     * <p>建议仅在「老版本存在严重 bug / 协议破坏性升级」时才勾选，其他情况留 false 减少用户骚扰。
     */
    @Column(name = "is_force", nullable = false)
    private Boolean isForce = false;

    /**
     * 是否已发布。
     * <ul>
     *   <li>{@code true} ：App 端「最新版本」查询会包含此条记录；</li>
     *   <li>{@code false}：admin 草稿态，App 端永远拿不到此条记录（即便 versionCode 是最大的）。</li>
     * </ul>
     * <p>典型场景：先在后台录好新版本，等应用商店审核通过 / 灰度结束，再切 true 让所有用户能看到。
     */
    @Column(name = "is_published", nullable = false)
    private Boolean isPublished = false;
}
