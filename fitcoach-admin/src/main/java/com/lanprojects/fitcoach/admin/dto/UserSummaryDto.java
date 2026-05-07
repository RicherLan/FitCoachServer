package com.lanprojects.fitcoach.admin.dto;

import com.lanprojects.fitcoach.login.entity.User;
import lombok.Builder;
import lombok.Data;

/**
 * 用户列表项 DTO（admin 后台用，不暴露 openId / unionId 等敏感字段）
 */
@Data
@Builder
public class UserSummaryDto {
    private String uid;
    private String nickname;
    private String avatarUrl;
    private String loginType;
    private Integer gender;
    /** 手机号，已脱敏（中间 4 位 *） */
    private String phoneMasked;
    private Boolean enabled;
    private Long createdAt;
    private Long lastLoginAt;

    public static UserSummaryDto from(User user, String avatarUrlAbsolute, String phoneMasked) {
        return UserSummaryDto.builder()
                .uid(user.getUid())
                .nickname(user.getNickname())
                .avatarUrl(avatarUrlAbsolute)
                .loginType(user.getLoginType() == null ? null : user.getLoginType().name())
                .gender(user.getGender())
                .phoneMasked(phoneMasked)
                .enabled(user.getEnabled())
                .createdAt(toMillis(user.getCreatedAt()))
                .lastLoginAt(toMillis(user.getLastLoginAt()))
                .build();
    }

    private static Long toMillis(java.time.LocalDateTime t) {
        return t == null ? null : t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
