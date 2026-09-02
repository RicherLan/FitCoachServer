package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.dto.GoogleIdTokenPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Google Sign In 服务（阶段 3B 波 2）—— 只做"配置 + 校验编排"，
 * 真正的 JWT 校验逻辑委托给 {@link GoogleTokenVerifier}。
 *
 * <p><b>配置来源</b>（{@code sys_config} 表）：
 * <ul>
 *   <li>{@link #CONFIG_ENABLED}     —— 总开关，false 时直接拒绝所有 Google 登录请求；</li>
 *   <li>{@link #CONFIG_CLIENT_IDS}  —— 允许的 OAuth Client ID 列表（逗号分隔）；
 *                                     Google 侧通常需要为 iOS / Android / Web 分别申请三个 Client ID，
 *                                     都要在此登记以便 aud 校验放行。</li>
 * </ul>
 *
 * <p><b>与 Apple 的配置命名一致</b>：{@code google_login.enabled / google_login.client_ids}，
 * 与 {@code apple_login.*} 平行对齐，运维两端配置心智一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleService {

    // ====== sys_config 配置键 ======
    public static final String CONFIG_ENABLED = "google_login.enabled";
    public static final String CONFIG_CLIENT_IDS = "google_login.client_ids";

    private final SysConfigService sysConfigService;
    private final GoogleTokenVerifier googleTokenVerifier;

    /**
     * 校验客户端上报的 idToken，返回解析后的载荷。
     *
     * @param idToken Google SDK 返回的 idToken
     * @return 校验通过后的载荷
     * @throws BusinessException 配置缺失 / 总开关关闭 / token 不合法 均抛业务异常
     */
    public GoogleIdTokenPayload verifyIdToken(String idToken) {
        ensureEnabled();
        Set<String> allowedAudiences = loadAllowedAudiences();
        return googleTokenVerifier.verify(idToken, allowedAudiences);
    }

    // ====== 内部 ======

    private void ensureEnabled() {
        String enabled = sysConfigService.getValue(CONFIG_ENABLED);
        if (enabled == null || !"true".equalsIgnoreCase(enabled.trim())) {
            log.warn("[google] Google 登录总开关未开启, key={}", CONFIG_ENABLED);
            throw new BusinessException(ResultCode.GOOGLE_CONFIG_MISSING,
                    "Google 登录未开启，请联系管理员在 sys_config 中启用 " + CONFIG_ENABLED);
        }
    }

    private Set<String> loadAllowedAudiences() {
        String raw = sysConfigService.getValue(CONFIG_CLIENT_IDS);
        if (raw == null || raw.isBlank()) {
            log.error("[google] Google Client ID 列表未配置, key={}", CONFIG_CLIENT_IDS);
            throw new BusinessException(ResultCode.GOOGLE_CONFIG_MISSING,
                    "Google Client ID 未配置，请在 sys_config 中设置 " + CONFIG_CLIENT_IDS);
        }
        Set<String> audiences = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (audiences.isEmpty()) {
            throw new BusinessException(ResultCode.GOOGLE_CONFIG_MISSING,
                    "Google Client ID 列表解析后为空，请检查 " + CONFIG_CLIENT_IDS + " 的值");
        }
        return audiences;
    }
}
