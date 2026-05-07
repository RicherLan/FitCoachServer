package com.lanprojects.fitcoach.login.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.upload.UploadProperties;
import com.lanprojects.fitcoach.login.dto.LoginResponse;
import com.lanprojects.fitcoach.login.dto.UpdateProfileRequest;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 用户资料业务 — 昵称 / 性别更新 + 头像上传。
 * <p>
 * 设计说明：
 * <ul>
 *   <li>上层 Controller 只负责协议适配（取 token、组装 Result）；</li>
 *   <li>所有校验都在这里做，错误码统一用 {@link ResultCode}；</li>
 *   <li>头像上传与 user 字段更新分开两个接口，避免一次 multipart 同时塞文件 + JSON
 *       带来的 Spring 解析复杂度。客户端先上传头像拿 URL，再统一调 PATCH 即可。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    /** 昵称最小长度（字符数，按 codePoint 计） */
    private static final int NICKNAME_MIN = 2;
    /** 昵称最大长度（字符数） */
    private static final int NICKNAME_MAX = 20;

    private final UserRepository userRepository;
    private final AvatarStorageService avatarStorageService;
    private final UploadProperties uploadProperties;

    /**
     * 更新昵称 / 性别等基础资料。
     * <p>所有字段都是可选，全部为 null 时视为非法请求（避免空 PATCH 被滥用）。
     */
    @Transactional
    public LoginResponse updateProfile(String uid, UpdateProfileRequest request) {
        if (request == null
                || (request.getNickname() == null && request.getGender() == null)) {
            throw new BusinessException(ResultCode.PROFILE_NO_CHANGES);
        }
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        if (!user.getEnabled()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        if (request.getNickname() != null) {
            String nickname = normalizeNickname(request.getNickname());
            user.setNickname(nickname);
        }
        if (request.getGender() != null) {
            int g = request.getGender();
            if (g != 0 && g != 1 && g != 2) {
                throw new BusinessException(ResultCode.GENDER_INVALID);
            }
            user.setGender(g);
        }

        userRepository.save(user);
        log.info("用户资料更新成功, uid={}, nickname={}, gender={}",
                uid, user.getNickname(), user.getGender());
        return toLoginResponse(user);
    }

    /**
     * 上传头像并把新 URL 写回 user.avatarUrl。
     * <p>校验：非空 / size / contentType；本地落盘由 {@link AvatarStorageService} 实现。
     */
    @Transactional
    public LoginResponse updateAvatar(String uid, MultipartFile file) {
        validateAvatar(file);
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        if (!user.getEnabled()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        String url = avatarStorageService.saveAvatar(uid, file);
        user.setAvatarUrl(url);
        userRepository.save(user);
        log.info("用户头像更新成功, uid={}, url={}", uid, url);
        return toLoginResponse(user);
    }

    // ====== 内部 ======

    /**
     * 昵称规范化 + 校验：
     * <ul>
     *   <li>trim 首尾空白；</li>
     *   <li>长度按 codePoint 计（emoji 算 1 字符），范围 [2, 20]；</li>
     *   <li>不允许仅含空白字符的 trim 后空串。</li>
     * </ul>
     */
    private String normalizeNickname(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ResultCode.NICKNAME_INVALID);
        }
        long len = trimmed.codePoints().count();
        if (len < NICKNAME_MIN || len > NICKNAME_MAX) {
            throw new BusinessException(ResultCode.NICKNAME_INVALID);
        }
        // 控制字符（除了普通空格）禁止，避免日志/UI 渲染异常
        for (int i = 0; i < trimmed.length(); ) {
            int cp = trimmed.codePointAt(i);
            if (cp != ' ' && Character.isISOControl(cp)) {
                throw new BusinessException(ResultCode.NICKNAME_INVALID);
            }
            i += Character.charCount(cp);
        }
        return trimmed;
    }

    private void validateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.AVATAR_FILE_EMPTY);
        }
        UploadProperties.Avatar cfg = uploadProperties.getAvatar();
        if (file.getSize() > cfg.getMaxSizeBytes()) {
            throw new BusinessException(ResultCode.AVATAR_FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null
                || !cfg.getAllowedContentTypesView().contains(contentType.toLowerCase())) {
            throw new BusinessException(ResultCode.AVATAR_CONTENT_TYPE_INVALID);
        }
    }

    /**
     * 转 LoginResponse —— /me 风格，不带 token / refreshToken / expiresIn。
     * <p>与 {@code AuthService.getCurrentUser} 返回结构保持一致，客户端解析无需分支。
     */
    private LoginResponse toLoginResponse(User user) {
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

    private Long toMillis(LocalDateTime time) {
        if (time == null) return null;
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
