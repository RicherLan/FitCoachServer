package com.lanprojects.fitcoach.admin.dto.testaccount;

import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.service.TestLoginService;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 端"测试账号"列表/详情/写操作返回 DTO。
 *
 * <p>测试账号 = {@link User#getLoginType()} 为 {@link User.LoginType#TEST} 的 user 行。
 * 通过 {@link TestLoginService#TEST_UID_PREFIX} 前缀 + 短账号名（如 {@code test_test1}）拼成 uid。
 *
 * <p><b>不包含 passwordHash</b>：BCrypt hash 不暴露给前端，重置密码必须走专门的接口。
 */
@Data
@Builder
public class AdminTestAccountDto {

    /** user.id（admin 路由 path 中用） */
    private Long id;

    /** 短账号名（uid 去掉 test_ 前缀），客户端登录时输入的就是这个值 */
    private String account;

    /** 完整 user.uid，例 "test_test1"；展示用，admin 一般不直接 copy */
    private String uid;

    /** 昵称，QA 在 App 里看到的就是这个 */
    private String nickname;

    /** 启用状态；禁用后客户端登录会被拒（USER_DISABLED） */
    private Boolean enabled;

    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从 user entity 构造 DTO。
     * <p>会校验 loginType 必须是 TEST —— 防御性兜底，避免别处误传非测试账号进来。
     */
    public static AdminTestAccountDto from(User u) {
        String uid = u.getUid();
        String account = uid != null && uid.startsWith(TestLoginService.TEST_UID_PREFIX)
                ? uid.substring(TestLoginService.TEST_UID_PREFIX.length())
                : uid;  // 兼容历史脏数据，理论上不会走到
        return AdminTestAccountDto.builder()
                .id(u.getId())
                .account(account)
                .uid(uid)
                .nickname(u.getNickname())
                .enabled(u.getEnabled())
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }
}
