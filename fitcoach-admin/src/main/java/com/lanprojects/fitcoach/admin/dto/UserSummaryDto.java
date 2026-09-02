package com.lanprojects.fitcoach.admin.dto;

import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.service.UserActivityService;
import lombok.Builder;
import lombok.Data;

/**
 * 用户列表项 DTO（admin 后台用，不暴露 openId / unionId 等敏感字段）
 */
@Data
@Builder
public class UserSummaryDto {
    private String uid;
    /** 用户号（{@link User#getAccount()}）—— 8 位纯数字，admin 后台搜索 / 客服查询主键 */
    private String account;
    private String nickname;
    private String avatarUrl;
    /** 最近一次登录方式（WECHAT / PHONE / ACCOUNT / GOOGLE / APPLE / ...） */
    private String loginType;
    /** 注册来源（首次创建该 user 时的渠道），与 loginType 不同，本字段永不变更 */
    private String registrationSource;
    /**
     * 用户首次注册时的 App Flavor（CN / GLOBAL / null=未标注）。永不变更，用于运营侧按市场分析用户来源。
     * <p>阶段 6 波 1 新增；老用户 null，Admin 前端按 UNKNOWN 展示。
     */
    private String registerFlavor;
    private Integer gender;
    /** 手机号，已脱敏（中间 4 位 *） */
    private String phoneMasked;
    private Boolean enabled;
    private Long createdAt;
    private Long lastLoginAt;
    /** 最后活跃时间（毫秒），由客户端 120s 轮询心跳更新；从未活跃为 null */
    private Long lastActiveAt;
    /** 是否在线：服务端按 ONLINE_WINDOW_MS（5min）窗口判断，前端直接展示 */
    private Boolean online;

    public static UserSummaryDto from(User user, String avatarUrlAbsolute, String phoneMasked) {
        return UserSummaryDto.builder()
                .uid(user.getUid())
                .account(user.getAccount())
                .nickname(user.getNickname())
                .avatarUrl(avatarUrlAbsolute)
                .loginType(user.getLoginType() == null ? null : user.getLoginType().name())
                .registrationSource(user.getRegistrationSource() == null ? null : user.getRegistrationSource().name())
                .registerFlavor(user.getRegisterFlavor() == null ? null : user.getRegisterFlavor().name())
                .gender(user.getGender())
                .phoneMasked(phoneMasked)
                .enabled(user.getEnabled())
                .createdAt(toMillis(user.getCreatedAt()))
                .lastLoginAt(toMillis(user.getLastLoginAt()))
                .lastActiveAt(toMillis(user.getLastActiveAt()))
                .online(UserActivityService.isOnline(user.getLastActiveAt()))
                .build();
    }

    private static Long toMillis(java.time.LocalDateTime t) {
        return t == null ? null : t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
