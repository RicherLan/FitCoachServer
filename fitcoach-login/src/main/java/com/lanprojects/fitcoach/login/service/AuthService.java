package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.client.ClientContext;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.upload.UploadProperties;
import com.lanprojects.fitcoach.common.util.LogUtils;
import com.lanprojects.fitcoach.login.dto.AppleIdTokenPayload;
import com.lanprojects.fitcoach.login.dto.AppleLoginRequest;
import com.lanprojects.fitcoach.login.dto.GoogleIdTokenPayload;
import com.lanprojects.fitcoach.login.dto.GoogleLoginRequest;
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
    private final AppleService appleService;
    private final GoogleService googleService;
    private final UserRepository userRepository;
    private final SysConfigService sysConfigService;
    private final UploadProperties uploadProperties;
    private final AccountGenerator accountGenerator;
    private final TokenBlacklistService tokenBlacklistService;

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
     * 手机号验证码登录
     * <p>
     * 流程：
     * <ol>
     *   <li>客户端先调 /api/auth/phone/sendCode 发码（OtpService 已写入缓存）；</li>
     *   <li>客户端再调 /api/auth/phone/login 把 phone + code 传过来；</li>
     *   <li>这里只负责 findOrCreate + 颁 token；OTP 校验由 Controller 层先做。</li>
     * </ol>
     *
     * <p>注意：OTP 校验前置由 Controller 调 OtpService.verifyOtp，校验通过才会进入本方法。
     * 这样防止"未校验就建账号"的逻辑漏洞。
     */
    @Transactional
    public LoginResponse phoneLogin(String phone) {
        User user = findOrCreateByPhone(phone);
        log.info("手机号登录成功, uid={}, phone={}", user.getUid(), LogUtils.mask(phone));
        return buildLoginResponse(user);
    }

    /**
     * Apple Sign In 登录（阶段 3B 波 1）。
     * <p>流程：
     * <ol>
     *   <li>{@link AppleService#verifyIdentityToken(String)} 拉 Apple JWK 验签 + 校验 iss/aud/exp 并解析 sub/email；</li>
     *   <li>按 sub 查找或创建 user，首次登录时把 email/fullName 持久化（Apple 只在首次授权时返回）；</li>
     *   <li>颁发 access + refresh token 并 rotate sessionId。</li>
     * </ol>
     * <p><b>关键 Apple 特性</b>：identityToken 内的 sub 是"该 App（Team）下的用户终身唯一 ID"，
     * 用作我们本地 user 的三方账户关联主键；跨 App / Team 的同一 Apple ID 会得到不同的 sub。
     */
    @Transactional
    public LoginResponse appleLogin(AppleLoginRequest request) {
        log.info("Apple 登录开始, hasEmail={}, hasFullName={}",
                request.getEmail() != null, request.getFullName() != null);

        AppleIdTokenPayload payload = appleService.verifyIdentityToken(request.getIdentityToken());
        User user = findOrCreateByApple(payload, request);

        log.info("Apple 登录成功, uid={}, appleSubMask={}", user.getUid(), LogUtils.mask(payload.sub()));
        return buildLoginResponse(user);
    }

    /**
     * Google Sign In 登录（阶段 3B 波 2）。
     * <p>流程：
     * <ol>
     *   <li>{@link GoogleService#verifyIdToken(String)} 拉 Google JWK 验签 + 校验 iss/aud/exp 并解析 sub/email/name/picture；</li>
     *   <li>按 sub 查找或创建 user，首次登录时把 email/name/picture 持久化；</li>
     *   <li>颁发 access + refresh token 并 rotate sessionId。</li>
     * </ol>
     * <p><b>与 Apple 的差异</b>：Google idToken 每次登录都返回完整用户信息，客户端不需要额外传 email/name。
     */
    @Transactional
    public LoginResponse googleLogin(GoogleLoginRequest request) {
        log.info("Google 登录开始");

        GoogleIdTokenPayload payload = googleService.verifyIdToken(request.getIdToken());
        User user = findOrCreateByGoogle(payload);

        log.info("Google 登录成功, uid={}, googleSubMask={}", user.getUid(), LogUtils.mask(payload.sub()));
        return buildLoginResponse(user);
    }

    /**
     * 对外暴露的"已存在用户登录"通用入口 —— 由其它已校验凭证的登录方式（如「账号 + 密码」
     * {@link PasswordService#loginByAccount(String, String, String)}）调用，
     * 复用 {@link #buildLoginResponse(User)} 的 sessionId 滚动 / JWT 签发逻辑，避免每个登录入口都重复造轮子。
     * <p>调用方需自己完成"用户存在性 + 账号启用 + 凭证校验"。本方法负责：
     * <ol>
     *   <li>把本次登录方式写到 {@code user.loginType}（最近登录方式语义）；</li>
     *   <li>刷新 {@code user.lastLoginAt}；</li>
     *   <li>颁 token + rotate sessionId（互踢逻辑）。</li>
     * </ol>
     *
     * @param user           已通过校验的 user，不能为 null
     * @param latestLoginType 本次实际使用的登录方式（用于写入 {@code user.loginType}）
     * @return 完整 LoginResponse（含 access/refresh token）
     */
    @Transactional
    public LoginResponse loginExistingUser(User user, User.LoginType latestLoginType) {
        user.setLoginType(latestLoginType);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        return buildLoginResponse(user);
    }

    /**
     * 通过 access token 获取当前用户信息。
     * <p>同时校验单设备登录互踢：jwt.sid 与 user.currentSessionId 不一致即抛
     * {@link ResultCode#SESSION_KICKED}。无 sid claim 的老 token 跳过该校验（向后兼容）。
     */
    public LoginResponse getCurrentUser(String accessToken) {
        User user = parseAndAssertSession(accessToken, JwtUtils.TYPE_ACCESS);

        // /me 接口只返回基础信息，不再附带 token / refreshToken
        return LoginResponse.builder()
                .uid(user.getUid())
                .account(user.getAccount())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .loginType(user.getLoginType().name())
                .createTime(toMillis(user.getCreatedAt()))
                .lastLoginTime(toMillis(user.getLastLoginAt()))
                .build();
    }

    /**
     * 获取 token 的剩余有效期（毫秒）。
     * <p>供 {@code AuthController.logout()} 调用，用作黑名单缓存的 TTL。
     *
     * @param token JWT token
     * @return 剩余有效期毫秒数；已过期或解析失败返回 0
     */
    public long getTokenRemainingMs(String token) {
        return JwtUtils.getRemainingMs(token, requireJwtSecret());
    }

    /**
     * 用 refresh token 换取新的 access token + refresh token（refresh 也滚动续期）。
     * <p>refreshToken 也参与单设备互踢校验：旧设备拿着旧 refreshToken 来续 → sid 不匹配即抛
     * {@link ResultCode#SESSION_KICKED}，避免被踢的设备靠 refresh 偷偷续命。
     * <p>校验通过后**沿用同一个 sessionId** 颁新 token —— 用户自己 refresh 不应该把自己挤掉。
     */
    @Transactional
    public LoginResponse refresh(String refreshToken) {
        User user = parseAndAssertSession(refreshToken, JwtUtils.TYPE_REFRESH);
        log.info("刷新 access token, uid={}", user.getUid());
        // 沿用旧 sid（user.currentSessionId 当前值）：refresh 不滚动 sessionId，
        // 不写库（avoid 写脏；user 没字段被改）
        return signTokensWithSession(user, user.getCurrentSessionId());
    }

    // ====== 内部方法 ======

    /**
     * 解析 token + 校验 user enabled + 单设备互踢 sid 比对。
     * <p>抽公共方法供 getCurrentUser / refresh 共用，保证语义一致。
     */
    private User parseAndAssertSession(String token, String expectedType) {
        // 黑名单检查：已登出的 token 立即拒绝，无需走后续解析和数据库查询
        if (tokenBlacklistService.isBlacklisted(token)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "token 已失效（已登出）");
        }

        String jwtSecret = requireJwtSecret();
        JwtUtils.JwtPayload payload = JwtUtils.parsePayload(token, jwtSecret, expectedType);

        User user = userRepository.findByUid(payload.uid())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        if (!user.getEnabled()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 单设备登录互踢校验：
        //  - token 带了 sid（说明是新版客户端 / 带 deviceId 的登录）→ 必须与 user.currentSessionId 一致；
        //  - token 没带 sid（老 token / admin 后台 / Postman）→ 跳过校验，保持向后兼容；
        //  - user.currentSessionId 为 null（用户从未通过带 deviceId 的客户端登录）→ 跳过校验，
        //    旧 token 自然过期/refresh 后会自动获得 sid。
        String tokenSid = payload.sessionId();
        String activeSid = user.getCurrentSessionId();
        if (tokenSid != null && activeSid != null && !tokenSid.equals(activeSid)) {
            log.info("session 互踢命中, uid={}, tokenSid={}, activeSid={}",
                    user.getUid(), LogUtils.mask(tokenSid), LogUtils.mask(activeSid));
            throw new BusinessException(ResultCode.SESSION_KICKED);
        }
        return user;
    }

    /**
     * 根据 user 颁发 access + refresh，组装完整 LoginResponse。
     * <p>登录入口（wechat/phone/password）调用本方法时，会触发"单设备登录互踢" sessionId 滚动逻辑：
     * 当 {@code ClientContext.get().hasDeviceId() == true} 时，无论同设备还是异设备登录都生成新 UUID
     * 写到 user.currentSessionId 并写入 JWT 的 sid claim；旧设备的旧 token 即刻在
     * {@link #parseAndAssertSession(String, String)} 处被拒。
     * <p>无 deviceId 的请求（admin / Postman / 客户端早期未就绪窗口）不写 currentSessionId、
     * JWT 也不带 sid，保持完全的向后兼容。
     */
    private LoginResponse buildLoginResponse(User user) {
        String newSessionId = rotateSessionForLogin(user);
        return signTokensWithSession(user, newSessionId);
    }

    /**
     * 仅做"颁 token"的纯函数：不动 user 表，按给定 sessionId 签出 access + refresh。
     * <p>登录走 {@link #buildLoginResponse(User)}（先 rotate sid 再调本方法）；
     * refresh 走"沿用旧 sid"路径直接调本方法。
     */
    private LoginResponse signTokensWithSession(User user, String sessionId) {
        String jwtSecret = requireJwtSecret();
        int accessHours = sysConfigService.getIntValue(CONFIG_JWT_EXPIRE_HOURS, DEFAULT_ACCESS_EXPIRE_HOURS);
        int refreshHours = sysConfigService.getIntValue(CONFIG_JWT_REFRESH_EXPIRE_HOURS, DEFAULT_REFRESH_EXPIRE_HOURS);

        String accessToken = JwtUtils.generateAccessToken(
                user.getUid(), jwtSecret, accessHours * 3600_000L, sessionId);
        String refreshToken = JwtUtils.generateRefreshToken(
                user.getUid(), jwtSecret, refreshHours * 3600_000L, sessionId);

        return LoginResponse.builder()
                .uid(user.getUid())
                .account(user.getAccount())
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

    /**
     * 登录时刷新 user 的 currentSessionId / currentDeviceId / currentLoginAt。
     * <p>仅当请求带真实 deviceId（{@code ClientContext.get().hasDeviceId() == true}）才落库；
     * 否则返回 null（JWT 也不带 sid claim，保持向后兼容）。
     * <p>注意：同设备重登也会翻新 sessionId，这是预期 —— 旧 token 立即作废，避免老设备保留无效会话。
     *
     * @return 新生成的 sessionId（null 表示本次登录不参与单设备互踢）
     */
    private String rotateSessionForLogin(User user) {
        var info = ClientContext.get();
        if (!info.hasDeviceId()) {
            // admin 后台 / Postman / 早期未就绪窗口：不参与互踢，保持向后兼容
            return null;
        }
        String deviceId = info.deviceId();
        String oldSid = user.getCurrentSessionId();
        String oldDeviceId = user.getCurrentDeviceId();
        boolean deviceChanged = oldDeviceId != null && !oldDeviceId.equals(deviceId);

        String newSid = UUID.randomUUID().toString().replace("-", "");
        user.setCurrentSessionId(newSid);
        user.setCurrentDeviceId(deviceId);
        user.setCurrentLoginAt(LocalDateTime.now());
        userRepository.save(user);

        if (deviceChanged) {
            // 真正的"换设备登录"：旧设备的下一个请求会拿到 SESSION_KICKED 被弹出
            log.info("device switch, uid={}, oldDevice={}, newDevice={}, oldSid={}, newSid={}",
                    user.getUid(), LogUtils.mask(oldDeviceId), LogUtils.mask(deviceId),
                    LogUtils.mask(oldSid), LogUtils.mask(newSid));
        } else {
            log.info("session rotated, uid={}, deviceId={}, newSid={}",
                    user.getUid(), LogUtils.mask(deviceId), LogUtils.mask(newSid));
        }
        return newSid;
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
            // 微信端若本次返回空头像（用户调隐私设置）→ 保留原头像，避免把用户已设置的头像清掉
            String wxAvatar = weChatUser.getHeadImgUrl();
            if (wxAvatar != null && !wxAvatar.isBlank()) {
                user.setAvatarUrl(wxAvatar);
            } else if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
                // 极端情况：本地无头像 + 微信也没给 → 兜底默认头像
                user.setAvatarUrl(resolveAvatarUrl(null));
            }
            user.setGender(weChatUser.getSex());
            user.setUnionId(tokenResp.getUnionId());
            // loginType = 最近登录方式 → 老用户用微信登录则覆写为 WECHAT
            user.setLoginType(User.LoginType.WECHAT);
            user.setLastLoginAt(LocalDateTime.now());
            // 兜底补 account（极小概率：DataInitializer migration 后又有新用户漏补）
            if (user.getAccount() == null || user.getAccount().isBlank()) {
                user.setAccount(accountGenerator.generateUnique());
            }
            return userRepository.save(user);
        }

        log.info("微信新用户注册, openId={}, unionId={}",
                LogUtils.mask(tokenResp.getOpenId()), LogUtils.mask(tokenResp.getUnionId()));
        User newUser = new User();
        newUser.setUid(UUID.randomUUID().toString().replace("-", ""));
        newUser.setAccount(accountGenerator.generateUnique());
        newUser.setNickname(weChatUser.getNickname());
        // 微信端可能返回空头像（比如用户隐私设置），缺失时用 server 配置的默认头像兜底
        newUser.setAvatarUrl(resolveAvatarUrl(weChatUser.getHeadImgUrl()));
        newUser.setLoginType(User.LoginType.WECHAT);
        newUser.setRegistrationSource(User.RegistrationSource.WECHAT);
        // 首次注册时锁定 App Flavor（阶段 2 多市场基建）；老客户端不上报为 null，日后统计可 filter
        newUser.setRegisterFlavor(ClientContext.appFlavor());
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

    /**
     * 按手机号查找或创建用户。
     * <p>并发安全：先查 → 不存在则插入；唯一索引冲突时回退按 phone 再查（同 wechat 路径）。
     */
    private User findOrCreateByPhone(String phone) {
        Optional<User> existing = userRepository.findByPhone(phone);
        if (existing.isPresent()) {
            User user = existing.get();
            log.info("手机号老用户登录, uid={}, phone={}", user.getUid(), LogUtils.mask(phone));
            // loginType = 最近登录方式 → 老用户用手机号登录则覆写为 PHONE
            user.setLoginType(User.LoginType.PHONE);
            user.setLastLoginAt(LocalDateTime.now());
            // 兜底补 account
            if (user.getAccount() == null || user.getAccount().isBlank()) {
                user.setAccount(accountGenerator.generateUnique());
            }
            return userRepository.save(user);
        }

        log.info("手机号新用户注册, phone={}", LogUtils.mask(phone));
        User newUser = new User();
        newUser.setUid(UUID.randomUUID().toString().replace("-", ""));
        newUser.setAccount(accountGenerator.generateUnique());
        // 默认昵称用手机号末 4 位脱敏，避免直接暴露用户标识
        newUser.setNickname("用户" + phone.substring(7));
        // 手机号注册无第三方头像源 → 直接用 server 配置的默认头像
        newUser.setAvatarUrl(resolveAvatarUrl(null));
        newUser.setLoginType(User.LoginType.PHONE);
        newUser.setRegistrationSource(User.RegistrationSource.PHONE);
        // 首次注册时锁定 App Flavor（阶段 2 多市场基建）；老客户端不上报为 null，日后统计可 filter
        newUser.setRegisterFlavor(ClientContext.appFlavor());
        newUser.setPhone(phone);
        newUser.setGender(0);
        newUser.setLastLoginAt(LocalDateTime.now());
        try {
            return userRepository.save(newUser);
        } catch (DataIntegrityViolationException e) {
            log.warn("并发创建手机号用户冲突，回退按 phone 查询, phone={}", LogUtils.mask(phone));
            return userRepository.findByPhone(phone)
                    .orElseThrow(() -> new BusinessException(ResultCode.ERROR, "用户创建失败，请重试"));
        }
    }

    /**
     * 按 Apple sub 查找或创建用户（阶段 3B 波 1）。
     * <p>Apple 特有语义：
     * <ul>
     *   <li>email / fullName 只在首次授权时返回，**后续登录 payload 内可能全为 null**，
     *       此时严禁覆盖已存字段（否则用户改过昵称会被抹掉）；</li>
     *   <li>用户可勾选"隐藏姓名"或"隐藏邮箱" → fullName / email 首次也可能为 null；
     *       此时 nickname 走 "Apple 用户" 兜底、appleEmail 落 null；</li>
     *   <li>appleSub 作为唯一索引 uk_apple_sub 保底并发 —— 冲突时回退按 sub 再查。</li>
     * </ul>
     */
    /**
     * 按 Google sub 查找或创建用户（阶段 3B 波 2）。
     * <p>Google 特点：
     * <ul>
     *   <li>每次登录都能拿到 email / name / picture，不像 Apple 只在首次授权时返回；</li>
     *   <li>但为保持"三方原始信息"的审计语义，本方法仍只在首次注册时写入
     *       {@code googleEmail} / {@code googleName}，二次登录不覆盖；</li>
     *   <li>{@code googleSub} 作为唯一索引 uk_google_sub 保底并发 —— 冲突时回退按 sub 再查。</li>
     * </ul>
     * <p>头像逻辑：Google 的 picture 是外网 URL（lh3.googleusercontent.com/...），
     * 首次注册直接落到 {@code avatarUrl}；后续登录不覆盖（用户可能已经上传自定义头像）。
     */
    private User findOrCreateByGoogle(GoogleIdTokenPayload payload) {
        String sub = payload.sub();
        Optional<User> existing = userRepository.findByGoogleSub(sub);
        if (existing.isPresent()) {
            User user = existing.get();
            log.info("Google 老用户登录, uid={}, googleSubMask={}", user.getUid(), LogUtils.mask(sub));
            user.setLoginType(User.LoginType.GOOGLE);
            user.setLastLoginAt(LocalDateTime.now());
            if (user.getAccount() == null || user.getAccount().isBlank()) {
                user.setAccount(accountGenerator.generateUnique());
            }
            // 老用户登录时不覆盖 email / name / avatar —— 保持用户在应用内的自定义修改
            return userRepository.save(user);
        }

        log.info("Google 新用户注册, googleSubMask={}, hasEmail={}, hasName={}",
                LogUtils.mask(sub), payload.hasEmail(), payload.hasName());

        User newUser = new User();
        newUser.setUid(UUID.randomUUID().toString().replace("-", ""));
        newUser.setAccount(accountGenerator.generateUnique());
        // 首次注册的 nickname 优先取 idToken 的 name；无 name 兜底 "Google 用户"
        String nickname = payload.hasName() ? payload.name().trim() : "Google 用户";
        if (nickname.length() > 20) {
            nickname = nickname.substring(0, 20);
        }
        newUser.setNickname(nickname);
        // 优先用 Google 返回的头像 URL，无则走默认头像
        newUser.setAvatarUrl(resolveAvatarUrl(payload.hasPicture() ? payload.picture() : null));
        newUser.setLoginType(User.LoginType.GOOGLE);
        newUser.setRegistrationSource(User.RegistrationSource.GOOGLE);
        newUser.setRegisterFlavor(ClientContext.appFlavor());
        newUser.setGoogleSub(sub);
        newUser.setGoogleEmail(payload.hasEmail() ? payload.email() : null);
        newUser.setGoogleName(payload.hasName() ? payload.name() : null);
        newUser.setGender(0);
        newUser.setLastLoginAt(LocalDateTime.now());

        try {
            return userRepository.save(newUser);
        } catch (DataIntegrityViolationException e) {
            log.warn("并发创建 Google 用户冲突，回退按 googleSub 查询, subMask={}", LogUtils.mask(sub));
            return userRepository.findByGoogleSub(sub)
                    .orElseThrow(() -> new BusinessException(ResultCode.ERROR, "用户创建失败，请重试"));
        }
    }

    private User findOrCreateByApple(AppleIdTokenPayload payload, AppleLoginRequest request) {
        String sub = payload.sub();
        Optional<User> existing = userRepository.findByAppleSub(sub);
        if (existing.isPresent()) {
            User user = existing.get();
            log.info("Apple 老用户登录, uid={}, appleSubMask={}", user.getUid(), LogUtils.mask(sub));
            user.setLoginType(User.LoginType.APPLE);
            user.setLastLoginAt(LocalDateTime.now());
            // 兜底补 account（DataInitializer migration 之后新用户不应触发此分支）
            if (user.getAccount() == null || user.getAccount().isBlank()) {
                user.setAccount(accountGenerator.generateUnique());
            }
            // 老用户登录时不覆盖 email / fullName —— Apple 后续登录本就不带这些字段，
            // 已存值可能是用户在设置里改过的，不能反向覆盖。
            return userRepository.save(user);
        }

        log.info("Apple 新用户注册, appleSubMask={}, hasEmail={}, hasFullName={}",
                LogUtils.mask(sub), payload.hasEmail() || (request.getEmail() != null),
                request.getFullName() != null);

        User newUser = new User();
        newUser.setUid(UUID.randomUUID().toString().replace("-", ""));
        newUser.setAccount(accountGenerator.generateUnique());
        // 首次注册的 nickname 优先取客户端传入的 fullName（Apple SDK 会拼接 given+family），
        // 无 fullName 兜底为 "Apple 用户"
        String nickname = (request.getFullName() != null && !request.getFullName().isBlank())
                ? request.getFullName().trim()
                : "Apple 用户";
        // 上限 20 字符（对齐 profile.nickname 校验规则），超长截断
        if (nickname.length() > 20) {
            nickname = nickname.substring(0, 20);
        }
        newUser.setNickname(nickname);
        // Apple 不返回头像 → 直接用 server 配置的默认头像
        newUser.setAvatarUrl(resolveAvatarUrl(null));
        newUser.setLoginType(User.LoginType.APPLE);
        newUser.setRegistrationSource(User.RegistrationSource.APPLE);
        // 首次注册时锁定 App Flavor（阶段 2 多市场基建）
        newUser.setRegisterFlavor(ClientContext.appFlavor());
        newUser.setAppleSub(sub);
        // 邮箱优先取 identityToken 里解析出的 email（更可信），兜底用 request.email（客户端 SDK 首次返回）
        String appleEmail = payload.hasEmail() ? payload.email() :
                (request.getEmail() != null && !request.getEmail().isBlank() ? request.getEmail().trim() : null);
        newUser.setAppleEmail(appleEmail);
        newUser.setAppleFullName(request.getFullName());
        newUser.setGender(0);
        newUser.setLastLoginAt(LocalDateTime.now());

        try {
            return userRepository.save(newUser);
        } catch (DataIntegrityViolationException e) {
            // 并发：另一个请求刚好把同 sub 的用户先建了；回退按 appleSub 重新查
            log.warn("并发创建 Apple 用户冲突，回退按 appleSub 查询, subMask={}", LogUtils.mask(sub));
            return userRepository.findByAppleSub(sub)
                    .orElseThrow(() -> new BusinessException(ResultCode.ERROR, "用户创建失败，请重试"));
        }
    }

    private Long toMillis(LocalDateTime time) {
        if (time == null) return null;
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 选择头像 URL：优先用第三方平台返回的，没有就走 server 配置的默认头像。
     * <p>defaultAvatarUrl 配置为空 / 留空时返回 null（数据库存 NULL，由客户端兜底渲染占位）。
     * @param sourceUrl 第三方头像 URL（如微信 headImgUrl），可能为 null / 空串
     * @return 实际写库的 avatarUrl
     */
    private String resolveAvatarUrl(String sourceUrl) {
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            return sourceUrl;
        }
        String fallback = uploadProperties.getDefaultAvatarUrl();
        return (fallback != null && !fallback.isBlank()) ? fallback : null;
    }
}
