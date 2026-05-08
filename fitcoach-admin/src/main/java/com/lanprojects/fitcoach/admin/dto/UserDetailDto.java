package com.lanprojects.fitcoach.admin.dto;

import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.service.UserActivityService;
import lombok.Builder;
import lombok.Data;

/**
 * 用户详情 DTO（admin 后台用），比 Summary 多展示 email / 第三方 ID 末尾片段。
 * <p>第三方 ID 完整暴露给后台管理员是合理需求（追踪绑定关系），但日志里仍要脱敏。
 */
@Data
@Builder
public class UserDetailDto {
    private String uid;
    private String nickname;
    private String avatarUrl;
    private String loginType;
    private Integer gender;
    private String phone;       // 后台允许查看完整手机号（业务必要）
    private String email;
    private String openId;
    private String unionId;
    private Boolean enabled;
    private Long createdAt;
    private Long updatedAt;
    private Long lastLoginAt;
    /** 最后活跃时间（毫秒），见 UserSummaryDto 同名字段说明 */
    private Long lastActiveAt;
    /** 是否在线 */
    private Boolean online;
    /** 该用户的反馈总数（关联统计） */
    private Long feedbackCount;

    public static UserDetailDto from(User user, String avatarUrlAbsolute, long feedbackCount) {
        return UserDetailDto.builder()
                .uid(user.getUid())
                .nickname(user.getNickname())
                .avatarUrl(avatarUrlAbsolute)
                .loginType(user.getLoginType() == null ? null : user.getLoginType().name())
                .gender(user.getGender())
                .phone(user.getPhone())
                .email(user.getEmail())
                .openId(user.getOpenId())
                .unionId(user.getUnionId())
                .enabled(user.getEnabled())
                .createdAt(toMillis(user.getCreatedAt()))
                .updatedAt(toMillis(user.getUpdatedAt()))
                .lastLoginAt(toMillis(user.getLastLoginAt()))
                .lastActiveAt(toMillis(user.getLastActiveAt()))
                .online(UserActivityService.isOnline(user.getLastActiveAt()))
                .feedbackCount(feedbackCount)
                .build();
    }

    private static Long toMillis(java.time.LocalDateTime t) {
        return t == null ? null : t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
