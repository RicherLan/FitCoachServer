package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.dto.AppleIdTokenPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Apple Sign In 服务（阶段 3B 波 1）—— 只做"配置 + 校验编排"，
 * 真正的 JWT 校验逻辑委托给 {@link AppleTokenVerifier}。
 *
 * <p><b>配置来源</b>（{@code sys_config} 表）：
 * <ul>
 *   <li>{@link #CONFIG_ENABLED}     —— 总开关，false 时直接拒绝所有 Apple 登录请求；</li>
 *   <li>{@link #CONFIG_CLIENT_IDS}  —— 允许的 audience 列表（逗号分隔），如 iOS Bundle ID + 未来 macOS bundle + Web Services ID；
 *                                     示例值：{@code com.lanprojects.fitcoach,com.lanprojects.fitcoach.web}。</li>
 * </ul>
 *
 * <p><b>为什么用逗号分隔的字符串而不是多行配置</b>：
 * <ul>
 *   <li>{@code sys_config} 表按 key 单值存储，加多行需改表结构；</li>
 *   <li>audience 列表通常 1-3 条，逗号分隔 UX 完全可接受；</li>
 *   <li>Trim / 大小写不敏感 由本类处理，运维填错时 fail-fast 报警。</li>
 * </ul>
 *
 * <p><b>与 fitcoach-payment 的 apple.* 配置隔离</b>：payment 用 {@code apple.bundle_id}
 * 是 IAP 收据校验的 audience；本处用 {@code apple_login.*} 前缀，两套配置在 sys_config 表里通过 key 前缀区分。
 * 通常两者的 bundle id 相同，但为了模块解耦不共享同一配置键。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppleService {

    // ====== sys_config 配置键 ======
    public static final String CONFIG_ENABLED = "apple_login.enabled";
    public static final String CONFIG_CLIENT_IDS = "apple_login.client_ids";

    private final SysConfigService sysConfigService;
    private final AppleTokenVerifier appleTokenVerifier;

    /**
     * 校验客户端上报的 identityToken，返回解析后的载荷。
     * <p>调用方（AuthService）再据此走 findOrCreateByApple + 颁 token 流程。
     *
     * @param identityToken Apple SDK 返回的 identityToken
     * @return 校验通过后的载荷
     * @throws BusinessException 配置缺失 / 总开关关闭 / token 不合法 均抛业务异常
     */
    public AppleIdTokenPayload verifyIdentityToken(String identityToken) {
        ensureEnabled();
        Set<String> allowedAudiences = loadAllowedAudiences();
        return appleTokenVerifier.verify(identityToken, allowedAudiences);
    }

    // ====== 内部 ======

    private void ensureEnabled() {
        String enabled = sysConfigService.getValue(CONFIG_ENABLED);
        // 未配置或显式 false 即视为未启用；避免"忘配置就默认开启"的意外
        if (enabled == null || !"true".equalsIgnoreCase(enabled.trim())) {
            log.warn("[apple] Apple 登录总开关未开启, key={}", CONFIG_ENABLED);
            throw new BusinessException(ResultCode.APPLE_CONFIG_MISSING,
                    "Apple 登录未开启，请联系管理员在 sys_config 中启用 " + CONFIG_ENABLED);
        }
    }

    private Set<String> loadAllowedAudiences() {
        String raw = sysConfigService.getValue(CONFIG_CLIENT_IDS);
        if (raw == null || raw.isBlank()) {
            log.error("[apple] Apple audience 列表未配置, key={}", CONFIG_CLIENT_IDS);
            throw new BusinessException(ResultCode.APPLE_CONFIG_MISSING,
                    "Apple audience 未配置，请在 sys_config 中设置 " + CONFIG_CLIENT_IDS);
        }
        Set<String> audiences = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (audiences.isEmpty()) {
            throw new BusinessException(ResultCode.APPLE_CONFIG_MISSING,
                    "Apple audience 列表解析后为空，请检查 " + CONFIG_CLIENT_IDS + " 的值");
        }
        return audiences;
    }
}
