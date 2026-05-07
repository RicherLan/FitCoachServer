package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.util.LogUtils;
import com.lanprojects.fitcoach.login.dto.LoginResponse;
import com.lanprojects.fitcoach.login.dto.WeChatTokenResponse;
import com.lanprojects.fitcoach.login.dto.WeChatUserInfo;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.login.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * 认证服务 — 协调登录流程
 * <p>
 * 微信登录流程：
 * 1. 客户端传来微信授权码 code
 * 2. 调用微信 API 用 code 换 access_token + openid
 * 3. 调用微信 API 用 access_token 获取用户信息
 * 4. 查找或创建本地用户
 * 5. 生成 JWT access token + refresh token 返回
 * <p>
 * 安全改造：
 * - JWT 密钥不再有硬编码兜底，未配置直接抛 {@link ResultCode#JWT_SECRET_MISSING}；
 * - access token 默认 2h，refresh token 默认 7d；
 * - 日志中所有 code/openId/unionId 用 {@link LogUtils#mask(String)} 脱敏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // ====== JWT 配置键（存数据库） ======
    public static final String CONFIG_JWT_SECRET = "jwt.secret";
    public static final String CONFIG_JWT_EXPIRE_HOURS = "jwt.expire_hours";
    public static final String CONFIG_JWT_REFRESH_EXPIRE_HOURS = "jwt.refresh_expire_hours";

    private static final int DEFAULT_ACCESS_EXPIRE_HOURS = 2;
    private static final int DEFAULT_REFRESH_EXPIRE_HOURS = 168;

    private final WeChatService weChatService;
    private final UserRepository userRepository;
    private final SysConfigService sysConfigService;

    /**
     * 微信登录
     */
    @Transactional
    public LoginResponse wechatLogin(String code) {
        log.info("微信登录开始, code={}", LogUtils.mask(code));

        // Step 1: code 换 access_token
        WeChatTokenResponse tokenResp = weChatService.getAccessToken(code);

        // Step 2: 获取微信用户信息
        WeChatUserInfo weChatUser = weChatService.getUserInfo(tokenResp.getAccessToken(), tokenResp.getOpenId());

        // Step 3: 查找或创建用户
        User user = findOrCreateUser(tokenResp, weChatUser);

        // Step 4: 颁发 token
        log.info("微信登录成功, uid={}, nickname={}", user.getUid(), user.getNickname());
        return buildLoginResponse(user);
    }

    /**
     * 通过 access token 获取当前用户信息
     */
    public LoginResponse getCurrentUser(String accessToken) {
        String jwtSecret = requireJwtSecret();
        String uid = JwtUtils.parseAndVerify(accessToken, jwtSecret, JwtUtils.TYPE_ACCESS);

        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        if (!user.getEnabled()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // /me 接口只返回基础信息，不再附带 token / refreshToken
        return LoginResponse.builder()
                .uid(user.getUid())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .loginType(user.getLoginType().name())
                .createTime(toMillis(user.getCreatedAt()))
                .lastLoginTime(toMillis(user.getLastLoginAt()))
                .build();
    }

    /**
     * 用 refresh token 换取新的 access token + refresh token（refresh 也滚动续期）
     */
    @Transactional
    public LoginResponse refresh(String refreshToken) {
        String jwtSecret = requireJwtSecret();
        String uid = JwtUtils.parseAndVerify(refreshToken, jwtSecret, JwtUtils.TYPE_REFRESH);

        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        if (!user.getEnabled()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        log.info("刷新 access token, uid={}", uid);
        return buildLoginResponse(user);
    }

    // ====== 内部方法 ======

    /**
     * 根据 user 颁发 access + refresh，组装完整 LoginResponse
     */
    private LoginResponse buildLoginResponse(User user) {
        String jwtSecret = requireJwtSecret();
        int accessHours = sysConfigService.getIntValue(CONFIG_JWT_EXPIRE_HOURS, DEFAULT_ACCESS_EXPIRE_HOURS);
        int refreshHours = sysConfigService.getIntValue(CONFIG_JWT_REFRESH_EXPIRE_HOURS, DEFAULT_REFRESH_EXPIRE_HOURS);

        String accessToken = JwtUtils.generateAccessToken(user.getUid(), jwtSecret, accessHours * 3600_000L);
        String refreshToken = JwtUtils.generateRefreshToken(user.getUid(), jwtSecret, refreshHours * 3600_000L);

        return LoginResponse.builder()
                .uid(user.getUid())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .loginType(user.getLoginType().name())
                .token(accessToken)
                .expiresIn(accessHours * 3600L)
                .refreshToken(refreshToken)
                .refreshExpiresIn(refreshHours * 3600L)
                .createTime(toMillis(user.getCreatedAt()))
                .lastLoginTime(toMillis(user.getLastLoginAt()))
                .build();
    }

    private String requireJwtSecret() {
        String secret = sysConfigService.getValue(CONFIG_JWT_SECRET);
        if (secret == null || secret.isBlank()) {
            log.error("JWT 密钥未配置！请检查 sys_config 表中的 {}", CONFIG_JWT_SECRET);
            throw new BusinessException(ResultCode.JWT_SECRET_MISSING);
        }
        return secret;
    }

    /**
     * 查找或创建用户。
     * <p>优先 unionId → 再 openId；并发场景下若唯一索引冲突，回退一次再查。</p>
     */
    private User findOrCreateUser(WeChatTokenResponse tokenResp, WeChatUserInfo weChatUser) {
        Optional<User> existing = Optional.<User>empty()
                .or(() -> Optional.ofNullable(tokenResp.getUnionId()).flatMap(userRepository::findByUnionId))
                .or(() -> userRepository.findByOpenId(tokenResp.getOpenId()));

        if (existing.isPresent()) {
            User user = existing.get();
            log.info("微信老用户登录, uid={}, openId={}", user.getUid(), LogUtils.mask(tokenResp.getOpenId()));
            user.setNickname(weChatUser.getNickname());
            user.setAvatarUrl(weChatUser.getHeadImgUrl());
            user.setGender(weChatUser.getSex());
            user.setUnionId(tokenResp.getUnionId());
            user.setLastLoginAt(LocalDateTime.now());
            return userRepository.save(user);
        }

        log.info("微信新用户注册, openId={}, unionId={}",
                LogUtils.mask(tokenResp.getOpenId()), LogUtils.mask(tokenResp.getUnionId()));
        User newUser = new User();
        newUser.setUid(UUID.randomUUID().toString().replace("-", ""));
        newUser.setNickname(weChatUser.getNickname());
        newUser.setAvatarUrl(weChatUser.getHeadImgUrl());
        newUser.setLoginType(User.LoginType.WECHAT);
        newUser.setOpenId(tokenResp.getOpenId());
        newUser.setUnionId(tokenResp.getUnionId());
        newUser.setGender(weChatUser.getSex());
        newUser.setLastLoginAt(LocalDateTime.now());
        try {
            return userRepository.save(newUser);
        } catch (DataIntegrityViolationException e) {
            // 并发：另一个请求刚好把同 openId / unionId 的用户先建了；回退用 openId 重新查
            log.warn("并发创建用户冲突，回退按 openId 查询, openId={}", LogUtils.mask(tokenResp.getOpenId()));
            return userRepository.findByOpenId(tokenResp.getOpenId())
                    .orElseThrow(() -> new BusinessException(ResultCode.ERROR, "用户创建失败，请重试"));
        }
    }

    private Long toMillis(LocalDateTime time) {
        if (time == null) return null;
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
