package com.lanprojects.fitcoach.login.support;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.login.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 鉴权辅助类 — 把 Controller 里"从 Authorization 头解出 uid / userId"的胶水代码抽出来。
 *
 * <p>本工程不引入 Spring Security，所有受保护接口都靠 Controller 自己从 Authorization 头取 token。
 * 这个 helper 让三种业务 Controller (exercise / membership / payment) 不必各自重复
 * extractToken + getCurrentUser 的样板代码。
 *
 * <p><b>用法</b>：
 * <pre>{@code
 *   @RestController
 *   @RequiredArgsConstructor
 *   class XxxController {
 *       private final AuthSupport auth;
 *       @GetMapping("/foo")
 *       public Result<...> foo(@RequestHeader("Authorization") String authorization) {
 *           Long userId = auth.requireUserId(authorization);  // 一行搞定
 *           ...
 *       }
 *   }
 * }</pre>
 *
 * <p><b>语义</b>：
 * <ul>
 *   <li>{@link #requireUid(String)} 返回业务字符串 uid（与 JWT subject 对齐，user 表的 uid 字段）；</li>
 *   <li>{@link #requireUserId(String)} 返回数据库主键 Long id（业务表 user_id 列用这个）；</li>
 *   <li>两者都已在内部完成 token 校验 + user 是否存在 + user.enabled 检查（复用 AuthService.getCurrentUser）。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AuthSupport {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final UserRepository userRepository;

    /**
     * 解出当前用户的业务 uid（字符串）。token 缺失/非法/用户禁用时抛 401。
     */
    public String requireUid(String authorization) {
        // getCurrentUser 内部已做 token 校验 + 用户存在 / enabled 校验
        return authService.getCurrentUser(extractToken(authorization)).getUid();
    }

    /**
     * 解出当前用户的数据库主键 id（业务表 user_id 列存这个）。
     * <p>多查一次库，但避免业务模块自己塞一份 UserRepository。
     */
    public Long requireUserId(String authorization) {
        String uid = requireUid(authorization);
        return userRepository.findByUid(uid)
                .map(User::getId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
    }

    /**
     * 同时返回 uid + userId（部分接口需要落两个）。
     */
    public AuthIdentity requireIdentity(String authorization) {
        String uid = requireUid(authorization);
        Long userId = userRepository.findByUid(uid)
                .map(User::getId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        return new AuthIdentity(uid, userId);
    }

    /**
     * 与 {@link #requireUid} 一样校验，但返回 null 而不抛 401（少数公开接口可用，
     * 比如 /api/membership/plans 已登录展示个性化标签、未登录展示纯列表）。
     */
    public Long optionalUserId(String authorization) {
        if (authorization == null || authorization.isBlank()) return null;
        try {
            return requireUserId(authorization);
        } catch (BusinessException ignore) {
            return null;
        }
    }

    /** 从 "Bearer xxx" 头提取裸 token */
    private String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少 Authorization 请求头");
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authorization 必须以 'Bearer ' 开头");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Authorization 中的 token 为空");
        }
        return token;
    }

    public record AuthIdentity(String uid, Long userId) {}
}
