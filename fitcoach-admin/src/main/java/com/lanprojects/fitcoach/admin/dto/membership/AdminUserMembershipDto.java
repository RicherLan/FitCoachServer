package com.lanprojects.fitcoach.admin.dto.membership;

import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.membership.entity.UserMembership;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 端"用户会员状态"DTO（用户搜索后的展示 + 详情）。
 *
 * <p>合并了 user 基本信息 + membership 状态，避免前端 N+1。
 */
@Data
@Builder
public class AdminUserMembershipDto {

    // ====== 用户基本信息 ======
    private String uid;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private Boolean userEnabled;

    // ====== 会员状态 ======
    /** 是否当前生效 */
    private Boolean isActive;

    /** 当前套餐 code（无会员时 null） */
    private String planCode;

    /** 当前套餐显示名（admin 已 join 出来给前端） */
    private String planDisplayName;

    private LocalDateTime activatedAt;

    private LocalDateTime expiresAt;

    /** 最近一笔订单号（便于审计） */
    private String lastOrderId;

    /**
     * 用户从未开通过会员的工厂方法。
     */
    public static AdminUserMembershipDto fromUserOnly(User user) {
        return AdminUserMembershipDto.builder()
                .uid(user.getUid())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .phone(user.getPhone())
                .userEnabled(user.getEnabled())
                .isActive(false)
                .build();
    }

    public static AdminUserMembershipDto from(User user, UserMembership m, String planDisplayName) {
        return AdminUserMembershipDto.builder()
                .uid(user.getUid())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .phone(user.getPhone())
                .userEnabled(user.getEnabled())
                .isActive(m != null && m.isActive())
                .planCode(m == null ? null : m.getPlanCode())
                .planDisplayName(planDisplayName)
                .activatedAt(m == null ? null : m.getActivatedAt())
                .expiresAt(m == null ? null : m.getExpiresAt())
                .lastOrderId(m == null ? null : m.getLastOrderId())
                .build();
    }
}
