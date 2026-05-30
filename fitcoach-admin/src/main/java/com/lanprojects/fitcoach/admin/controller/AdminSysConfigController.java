package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.sysconfig.SysConfigDto;
import com.lanprojects.fitcoach.admin.dto.sysconfig.UpdateSysConfigRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import com.lanprojects.fitcoach.common.config.repository.SysConfigRepository;
import com.lanprojects.fitcoach.common.config.service.ConfigCryptoService;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置管理（admin 后台）。
 *
 * <p>路径前缀：{@code /api/admin/sys-config}
 * <ul>
 *   <li>{@code GET /} —— 全部配置列表（可选 ?group=xxx 按分组筛选）</li>
 *   <li>{@code GET /groups} —— 获取所有配置分组名称</li>
 *   <li>{@code PUT /{configKey}} —— 更新配置值（加密字段传明文，server 自动加密入库）</li>
 *   <li>{@code POST /refresh-cache} —— 手动刷新内存缓存</li>
 * </ul>
 *
 * <p>安全：读操作所有角色可用；写操作（PUT / POST）由 {@link AdminAuthInterceptor}
 * 对 VIEWER 拦截（canWrite 判定）。
 *
 * <p>加密字段（{@code encrypted=true}）在列表中 configValue 显示 "******"，
 * 仅在编辑提交时传入新的明文值才覆盖。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/sys-config")
@RequiredArgsConstructor
public class AdminSysConfigController {

    private final SysConfigRepository sysConfigRepository;
    private final SysConfigService sysConfigService;
    private final ConfigCryptoService configCryptoService;
    private final AdminAuditLogService auditLogService;

    /**
     * 列表 — 全部或按分组筛选。
     *
     * @param group 可选分组名（如 "captcha"、"wechat"、"jwt"、"payment"）；
     *              不传则返回全部。
     */
    @GetMapping
    public Result<List<SysConfigDto>> list(@RequestParam(value = "group", required = false) String group) {
        List<SysConfig> configs;
        if (group != null && !group.isBlank()) {
            configs = sysConfigRepository.findByConfigGroup(group);
        } else {
            configs = sysConfigRepository.findAll();
        }
        List<SysConfigDto> dtos = configs.stream()
                .map(SysConfigDto::from)
                .collect(Collectors.toList());
        return Result.success(dtos);
    }

    /**
     * 获取所有已存在的配置分组名称（去重、非空）。
     */
    @GetMapping("/groups")
    public Result<List<String>> groups() {
        List<String> groups = sysConfigRepository.findAll().stream()
                .map(SysConfig::getConfigGroup)
                .filter(g -> g != null && !g.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return Result.success(groups);
    }

    /**
     * 更新配置项。
     * <p>
     * PATCH 语义：只更新请求体中非 null 的字段。
     * 对于加密字段，{@code configValue} 传入明文，server 自动加密入库 + 更新缓存。
     * 如果 configValue 传 "******"（前端未修改），则跳过值的更新。
     *
     * @param configKey 配置键（URL path 变量）
     */
    @PutMapping("/{configKey}")
    public Result<SysConfigDto> update(
            HttpServletRequest request,
            @PathVariable("configKey") String configKey,
            @RequestBody UpdateSysConfigRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);

        SysConfig config = sysConfigRepository.findByConfigKey(configKey)
                .orElseThrow(() -> new BusinessException(ResultCode.SYS_CONFIG_NOT_FOUND));

        // 更新描述
        boolean descChanged = false;
        if (body.getDescription() != null) {
            config.setDescription(body.getDescription());
            descChanged = true;
        }

        // 更新启用状态
        boolean enabledChanged = false;
        if (body.getEnabled() != null) {
            config.setEnabled(body.getEnabled());
            enabledChanged = true;
        }

        // 更新值（加密字段传明文，server 自动加密）。
        // P1-18：审计 summary 只记录"值是否变更"而非真实值 —— sysConfig 可能存 jwt secret / api key 等敏感字段。
        boolean valueChanged = false;
        if (body.getConfigValue() != null && !"******".equals(body.getConfigValue())) {
            if (Boolean.TRUE.equals(config.getEncrypted())) {
                config.setConfigValue(configCryptoService.encrypt(body.getConfigValue()));
            } else {
                config.setConfigValue(body.getConfigValue());
            }
            valueChanged = true;
        }

        sysConfigRepository.save(config);

        // 同步刷新缓存，确保后续读取立即生效
        sysConfigService.refreshCache();

        log.info("[admin] {} 更新配置 key={} enabled={}", operator, configKey, config.getEnabled());
        String summary = String.format("encrypted=%s, valueChanged=%s, descChanged=%s, enabledChanged=%s, enabled=%s",
                Boolean.TRUE.equals(config.getEncrypted()),
                valueChanged, descChanged, enabledChanged, config.getEnabled());
        auditLogService.logSuccess(request, AdminAuditAction.UPDATE_SYS_CONFIG, "SYS_CONFIG", configKey, summary);
        return Result.success(SysConfigDto.from(config));
    }

    /**
     * 手动刷新配置缓存。
     * <p>
     * 正常情况下 {@link #update} 已经自动刷新；此接口作为运维保底手段，
     * 例如直接修改了数据库后调用。
     */
    @PostMapping("/refresh-cache")
    public Result<Void> refreshCache(HttpServletRequest request) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        sysConfigService.refreshCache();
        log.info("[admin] {} 手动刷新系统配置缓存", operator);
        auditLogService.logSuccess(request, AdminAuditAction.REFRESH_SYS_CONFIG_CACHE,
                "SYS_CONFIG", null, "manual cache refresh");
        return Result.success(null);
    }
}
