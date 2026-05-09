package com.lanprojects.fitcoach.appversion.dto;

import com.lanprojects.fitcoach.appversion.entity.AppVersionEntity;
import lombok.Builder;
import lombok.Data;

/**
 * App 端「检查更新」响应 DTO。
 *
 * <p><b>语义</b>：server 已替客户端做完「当前版本 vs 最新版本」的判定，
 * 客户端只需要看 {@link #hasUpdate} 决定是否弹窗，{@link #releaseNotes} 直接渲染弹窗内容，
 * 不需要在端上重复实现版本号比较逻辑（避免端 / server 两边比较规则不一致的 bug）。
 *
 * <p><b>没有可用更新时</b>（该平台无任何已发布记录 / 客户端版本已是最新）：
 * <ul>
 *   <li>{@code hasUpdate = false}</li>
 *   <li>{@code latestVersionName / latestVersionCode / releaseNotes / downloadUrl / isForce} 均为 null</li>
 *   <li>{@code currentVersionCode / platform} 仍回显，便于客户端日志排查</li>
 * </ul>
 */
@Data
@Builder
public class LatestVersionDTO {

    /** 是否有新版本可升级（server 已比对，客户端不需要重算） */
    private boolean hasUpdate;

    /** 最新版本号字符串（"1.2.3"），无更新时为 null */
    private String latestVersionName;

    /** 最新版本号数值（用于客户端二次校验，无更新时为 null） */
    private Integer latestVersionCode;

    /** 更新说明，无更新时为 null。客户端弹窗 body 直接渲染 */
    private String releaseNotes;

    /** 应用商店跳转链接，无更新时为 null。客户端 Linking.openURL 打开 */
    private String downloadUrl;

    /** 是否强制升级（true 时客户端弹窗只展示「去更新」按钮，无「稍后」），无更新时为 null */
    private Boolean isForce;

    /** 客户端当前版本号（来自请求 Header，回显用于客户端日志/排查） */
    private Integer currentVersionCode;

    /** 客户端平台（android / ios），来自请求 Header，回显 */
    private String platform;

    /**
     * 构造「无更新」响应 —— 服务器找不到该平台的已发布版本，或客户端已是最新。
     */
    public static LatestVersionDTO noUpdate(String platform, Integer currentVersionCode) {
        return LatestVersionDTO.builder()
                .hasUpdate(false)
                .platform(platform)
                .currentVersionCode(currentVersionCode)
                .build();
    }

    /**
     * 构造「有更新」响应 —— 把数据库里的最新版本元数据填到字段上。
     */
    public static LatestVersionDTO fromLatest(AppVersionEntity latest, Integer currentVersionCode) {
        return LatestVersionDTO.builder()
                .hasUpdate(true)
                .latestVersionName(latest.getVersionName())
                .latestVersionCode(latest.getVersionCode())
                .releaseNotes(latest.getReleaseNotes())
                .downloadUrl(latest.getDownloadUrl())
                .isForce(Boolean.TRUE.equals(latest.getIsForce()))
                .platform(latest.getPlatform())
                .currentVersionCode(currentVersionCode)
                .build();
    }
}
