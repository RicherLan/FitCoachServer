package com.lanprojects.fitcoach.appversion.controller;

import com.lanprojects.fitcoach.appversion.dto.LatestVersionDTO;
import com.lanprojects.fitcoach.appversion.entity.AppVersionEntity;
import com.lanprojects.fitcoach.appversion.service.AppVersionService;
import com.lanprojects.fitcoach.common.client.ClientContext;
import com.lanprojects.fitcoach.common.client.ClientVersionInfo;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * App 端「检查更新」控制器。
 *
 * <p>路径前缀：{@code /api/app/version}
 * <ul>
 *   <li>{@code GET /latest} —— 取该平台已发布的最新版本，并替客户端比对当前版本是否需要升级。</li>
 * </ul>
 *
 * <p><b>不强制登录</b>：检查更新对未登录用户也应该可用（用户可能因为版本太旧导致无法登录），
 * 因此本接口不验 token。client 信息通过请求 Header 上报，没传也能 fallback 到 query param。
 *
 * <p><b>判定规则</b>：客户端 nativeVersionCode &lt; 数据库最新已发布 versionCode → hasUpdate=true。
 * 等于或大于（用户灰度装了更新版）都判为「无更新」。
 */
@Slf4j
@RestController
@RequestMapping("/api/app/version")
@RequiredArgsConstructor
public class AppVersionController {

    private final AppVersionService appVersionService;

    /**
     * 拿该平台已发布的最新版本，并和客户端当前版本比对。
     *
     * <p><b>参数优先级</b>（覆盖式）：
     * <ol>
     *   <li>query 显式传的 {@code platform} / {@code currentVersionCode} 优先；</li>
     *   <li>未传时回落到 {@link ClientContext}（即 X-Client-Platform / X-Client-Native-Version-Code Header）。</li>
     * </ol>
     * <p>这样写既兼容标准 RN 客户端（自动带 Header），也方便 Postman / curl 测试时显式传参。
     *
     * @param platformParam        客户端平台（可选，缺失走 Header），合法值：android / ios
     * @param currentVersionCodeParam 客户端当前版本号（可选，缺失走 Header）
     */
    @GetMapping("/latest")
    public Result<LatestVersionDTO> getLatest(
            @RequestParam(value = "platform", required = false) String platformParam,
            @RequestParam(value = "currentVersionCode", required = false) Integer currentVersionCodeParam) {
        ClientVersionInfo client = ClientContext.get();

        // platform 优先取 query，再取 Header 的 ClientContext.platform()；都没有就抛 7702
        // —— 检查更新接口必须知道是哪个平台，否则没法判定
        String platform = (platformParam != null && !platformParam.isBlank())
                ? platformParam.toLowerCase()
                : client.platform();
        if (platform == null || platform.isBlank()) {
            throw new BusinessException(ResultCode.APP_VERSION_PLATFORM_INVALID);
        }

        // currentVersionCode 同理；缺失（admin / Postman）按 0 处理 — 任何已发布版本都被视作「有更新」
        int currentVersionCode = currentVersionCodeParam != null
                ? currentVersionCodeParam
                : client.nativeVersionCode();

        Optional<AppVersionEntity> latestOpt = appVersionService.findLatestPublished(platform);
        if (latestOpt.isEmpty()) {
            log.debug("[appversion] check update: platform={} no published record, currentVc={}",
                    platform, currentVersionCode);
            return Result.success(LatestVersionDTO.noUpdate(platform, currentVersionCode));
        }
        AppVersionEntity latest = latestOpt.get();
        boolean hasUpdate = currentVersionCode < latest.getVersionCode();
        log.info("[appversion] check update: platform={} currentVc={} latestVc={} latestVn={} hasUpdate={}",
                platform, currentVersionCode, latest.getVersionCode(), latest.getVersionName(), hasUpdate);
        return Result.success(hasUpdate
                ? LatestVersionDTO.fromLatest(latest, currentVersionCode)
                : LatestVersionDTO.noUpdate(platform, currentVersionCode));
    }
}
