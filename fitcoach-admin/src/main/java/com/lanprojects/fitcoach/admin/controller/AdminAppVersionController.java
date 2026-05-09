package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.dto.appversion.AdminAppVersionDto;
import com.lanprojects.fitcoach.admin.dto.appversion.AdminAppVersionRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.appversion.entity.AppVersionEntity;
import com.lanprojects.fitcoach.appversion.service.AppVersionService;
import com.lanprojects.fitcoach.common.model.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * App 版本管理（admin 后台）。
 *
 * <p>路径前缀：{@code /api/admin/app-versions}
 * <ul>
 *   <li>{@code GET /} —— 列表（含未发布草稿；可选 platform 过滤），按 platform/versionCode 倒序</li>
 *   <li>{@code GET /{id}} —— 详情</li>
 *   <li>{@code POST /} —— 创建（默认草稿态 isPublished=false）</li>
 *   <li>{@code PATCH /{id}} —— 更新（PATCH 语义；platform / versionCode 不可改）</li>
 *   <li>{@code POST /{id}/toggle-published?value=true|false} —— 一键发布/下线</li>
 *   <li>{@code DELETE /{id}} —— 硬删除</li>
 * </ul>
 *
 * <p><b>鉴权</b>：本路径走 {@link AdminAuthInterceptor}，只有登录的 admin 才能访问；
 * App 端检查更新接口在 {@code /api/app/version/latest}（无鉴权），与此处隔离。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/app-versions")
@RequiredArgsConstructor
public class AdminAppVersionController {

    private final AppVersionService appVersionService;

    /**
     * 列表。
     *
     * @param platform 可选平台过滤（android / ios）；不传则返回所有平台
     */
    @GetMapping
    public Result<List<AdminAppVersionDto>> list(
            @RequestParam(value = "platform", required = false) String platform) {
        List<AppVersionEntity> rows = (platform != null && !platform.isBlank())
                ? appVersionService.listByPlatform(platform.toLowerCase())
                : appVersionService.listAll();
        List<AdminAppVersionDto> records = rows.stream().map(AdminAppVersionDto::from).toList();
        return Result.success(records);
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<AdminAppVersionDto> detail(@PathVariable("id") Long id) {
        return Result.success(AdminAppVersionDto.from(appVersionService.findById(id)));
    }

    /** 创建（默认草稿态，需要后续 toggle-published 才对 App 端可见） */
    @PostMapping
    public Result<AdminAppVersionDto> create(
            HttpServletRequest request,
            @Validated(AdminAppVersionRequest.OnCreate.class) @RequestBody AdminAppVersionRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        AppVersionEntity saved = appVersionService.create(body.toCreateEntity());
        log.info("[admin] {} 创建版本 id={} platform={} versionName={} versionCode={}",
                operator, saved.getId(), saved.getPlatform(), saved.getVersionName(), saved.getVersionCode());
        return Result.success(AdminAppVersionDto.from(saved));
    }

    /** 更新（PATCH 语义；platform / versionCode 不可改） */
    @PatchMapping("/{id}")
    public Result<AdminAppVersionDto> update(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminAppVersionRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        AppVersionEntity updated = appVersionService.update(id, body.toPatchEntity());
        log.info("[admin] {} 更新版本 id={} platform={} versionCode={} isPublished={}",
                operator, updated.getId(), updated.getPlatform(), updated.getVersionCode(), updated.getIsPublished());
        return Result.success(AdminAppVersionDto.from(updated));
    }

    /** 一键发布/下线（草稿与正式之间切换） */
    @PostMapping("/{id}/toggle-published")
    public Result<AdminAppVersionDto> togglePublished(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @RequestParam("value") boolean value) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        AppVersionEntity patch = new AppVersionEntity();
        patch.setIsPublished(value);
        AppVersionEntity updated = appVersionService.update(id, patch);
        log.info("[admin] {} 切换版本 id={} 发布状态 → {}", operator, id, value);
        return Result.success(AdminAppVersionDto.from(updated));
    }

    /** 硬删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable("id") Long id) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        appVersionService.delete(id);
        log.info("[admin] {} 删除版本 id={}", operator, id);
        return Result.success(null);
    }
}
