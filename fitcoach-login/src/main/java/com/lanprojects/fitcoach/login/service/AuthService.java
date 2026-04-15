package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.dto.LoginResponse;
import com.lanprojects.fitcoach.login.dto.WeChatTokenResponse;
import com.lanprojects.fitcoach.login.dto.WeChatUserInfo;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.login.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 认证服务 — 协调登录流程
 * <p>
 * 微信登录流程：
 * 1. 客户端传来微信授权码 code
 * 2. 调用微信 API 用 code 换 access_token + openid
 * 3. 调用微信 API 用 access_token 获取用户信息
 * 4. 查找或创建本地用户
 * 5. 生成 JWT token 返回给客户端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // ====== JWT 配置键（存在数据库中） ======
    public static final String CONFIG_JWT_SECRET = "jwt.secret";
    public static final String CONFIG_JWT_EXPIRE_HOURS = "jwt.expire_hours";

    private static final String DEFAULT_JWT_SECRET = "FitCoach2026SecretKeyForJwtToken!!";
    private static final int DEFAULT_JWT_EXPIRE_HOURS = 168; // 7 天

    private final WeChatService weChatService;
    private final UserRepository userRepository;
    private final SysConfigService sysConfigService;

    /**
     * 微信登录
     *
     * @param code 微信授权码
     * @return 登录响应（用户信息 + JWT token）
     */
    @Transactional
    public LoginResponse wechatLogin(String code) {
        // Step 1: code 换 access_token
        log.info("微信登录开始, code={}...", code.substring(0, Math.min(6, code.length())));
        WeChatTokenResponse tokenResp = weChatService.getAccessToken(code);

        // Step 2: 获取微信用户信息
        WeChatUserInfo weChatUser = weChatService.getUserInfo(tokenResp.getAccessToken(), tokenResp.getOpenId());

        // Step 3: 查找或创建用户
        User user = findOrCreateUser(tokenResp, weChatUser);

        // Step 4: 生成 JWT
        String jwtSecret = sysConfigService.getValue(CONFIG_JWT_SECRET, DEFAULT_JWT_SECRET);
        int expireHours = sysConfigService.getIntValue(CONFIG_JWT_EXPIRE_HOURS, DEFAULT_JWT_EXPIRE_HOURS);
        long expireMs = (long) expireHours * 3600 * 1000;

        String token = JwtUtils.generateToken(user.getUid(), jwtSecret, expireMs);

        // Step 5: 构建响应
        log.info("微信登录成功, uid={}, nickname={}", user.getUid(), user.getNickname());
        return LoginResponse.builder()
                .uid(user.getUid())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .loginType(user.getLoginType().name())
                .token(token)
                .expiresIn(expireHours * 3600L)
                .build();
    }

    /**
     * 通过 token 获取当前用户信息
     */
    public LoginResponse getCurrentUser(String token) {
        String jwtSecret = sysConfigService.getValue(CONFIG_JWT_SECRET, DEFAULT_JWT_SECRET);
        String uid = JwtUtils.parseToken(token, jwtSecret);
        if (uid == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        if (!user.getEnabled()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        return LoginResponse.builder()
                .uid(user.getUid())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .loginType(user.getLoginType().name())
                .build();
    }

    // ====== 内部方法 ======

    /**
     * 查找已有用户 或 创建新用户
     */
    private User findOrCreateUser(WeChatTokenResponse tokenResp, WeChatUserInfo weChatUser) {
        // 优先用 unionId 查找（跨应用统一标识）
        User user = null;
        if (tokenResp.getUnionId() != null) {
            user = userRepository.findByUnionId(tokenResp.getUnionId()).orElse(null);
        }
        // 再用 openId 查找
        if (user == null) {
            user = userRepository.findByOpenId(tokenResp.getOpenId()).orElse(null);
        }

        if (user != null) {
            // 老用户 → 更新信息
            log.info("微信老用户登录, uid={}", user.getUid());
            user.setNickname(weChatUser.getNickname());
            user.setAvatarUrl(weChatUser.getHeadImgUrl());
            user.setGender(weChatUser.getSex());
            user.setUnionId(tokenResp.getUnionId());
            user.setLastLoginAt(LocalDateTime.now());
            return userRepository.save(user);
        } else {
            // 新用户 → 创建
            log.info("微信新用户注册, openId={}", tokenResp.getOpenId());
            User newUser = new User();
            newUser.setUid(UUID.randomUUID().toString().replace("-", ""));
            newUser.setNickname(weChatUser.getNickname());
            newUser.setAvatarUrl(weChatUser.getHeadImgUrl());
            newUser.setLoginType(User.LoginType.WECHAT);
            newUser.setOpenId(tokenResp.getOpenId());
            newUser.setUnionId(tokenResp.getUnionId());
            newUser.setGender(weChatUser.getSex());
            newUser.setLastLoginAt(LocalDateTime.now());
            return userRepository.save(newUser);
        }
    }
}
