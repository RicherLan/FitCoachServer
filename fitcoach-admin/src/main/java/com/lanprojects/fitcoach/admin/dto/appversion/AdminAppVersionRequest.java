package com.lanprojects.fitcoach.admin.dto.appversion;

import com.lanprojects.fitcoach.appversion.entity.AppVersionEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Admin 端 App 版本创建/更新入参。
 *
 * <p>语义约定：
 * <ul>
 *   <li>创建时必须传：platform / versionName / versionCode / downloadUrl；</li>
 *   <li>更新时（PATCH）所有字段可空，{@code null} 表示不动；</li>
 *   <li>{@code platform} / {@code versionCode} 一旦创建禁止改名（service 层会忽略 patch 中的这两字段，
 *       这里只在创建时使用）。</li>
 * </ul>
 *
 * <p><b>versionName 与 versionCode 由 admin 同时手填</b>（后端不自动从 versionName 反推 versionCode），
 * 因为 admin 录入时本就要按"MAJOR*1_000_000 + MINOR*1_000 + PATCH"规则填一对值。
 * 若 admin 想偷懒，前端可以做"输 versionName 自动算 versionCode"的便利校验。
 */
@Data
public class AdminAppVersionRequest {

    /**
     * 客户端平台。当前仅支持 android / ios。
     * <p>正则约束 + service 层 ALLOWED_PLATFORMS 双重保护。
     */
    @Pattern(regexp = "^(android|ios)$",
            message = "platform 仅支持 android / ios",
            groups = {OnCreate.class})
    @NotBlank(groups = OnCreate.class)
    private String platform;

    /** 展示版本号，例 "1.2.3" */
    @NotBlank(groups = OnCreate.class)
    @Size(max = 32)
    private String versionName;

    /** 数值版本号，例 1002003。必须 &gt; 0 */
    @NotNull(groups = OnCreate.class)
    @Positive(groups = OnCreate.class)
    private Integer versionCode;

    /** 更新说明（最多 2000 字，支持 \n 换行） */
    @Size(max = 2000)
    private String releaseNotes;

    /** 应用商店跳转链接 */
    @NotBlank(groups = OnCreate.class)
    @Size(max = 512)
    private String downloadUrl;

    /** 是否强制升级（创建时不传默认 false） */
    private Boolean isForce;

    /** 是否已发布（创建时不传默认 false，即草稿态） */
    private Boolean isPublished;

    /** 转换为新建用的 entity（仅创建时使用） */
    public AppVersionEntity toCreateEntity() {
        AppVersionEntity v = new AppVersionEntity();
        v.setPlatform(platform != null ? platform.toLowerCase() : null);
        v.setVersionName(versionName);
        v.setVersionCode(versionCode);
        v.setReleaseNotes(releaseNotes);
        v.setDownloadUrl(downloadUrl);
        v.setIsForce(isForce != null ? isForce : Boolean.FALSE);
        v.setIsPublished(isPublished != null ? isPublished : Boolean.FALSE);
        return v;
    }

    /**
     * 把 PATCH 请求里非 null 的字段叠加到 patch 实体（实际更新由
     * {@link com.lanprojects.fitcoach.appversion.service.AppVersionService#update} 完成）。
     * <p>platform / versionCode 不允许更新，故这里不设置（即便客户端传了也会被 service 忽略）。
     */
    public AppVersionEntity toPatchEntity() {
        AppVersionEntity patch = new AppVersionEntity();
        // platform 不允许更新（保护 (platform, versionCode) 的唯一约束语义）
        patch.setVersionName(versionName);
        // versionCode 不允许更新（同上）
        patch.setReleaseNotes(releaseNotes);
        patch.setDownloadUrl(downloadUrl);
        patch.setIsForce(isForce);
        patch.setIsPublished(isPublished);
        return patch;
    }

    /** 仅创建时校验 */
    public interface OnCreate {}
}
