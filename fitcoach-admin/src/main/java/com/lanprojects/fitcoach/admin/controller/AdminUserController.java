package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.UpdateUserStatusRequest;
import com.lanprojects.fitcoach.admin.dto.UserDetailDto;
import com.lanprojects.fitcoach.admin.dto.UserSummaryDto;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.admin.service.AdminUserService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理（admin 后台）。
 * <p>路径前缀：/api/admin/users
 * <ul>
 *   <li>{@code GET /} —— 分页列表，支持 keyword / enabled / loginType 过滤</li>
 *   <li>{@code GET /{uid}} —— 详情</li>
 *   <li>{@code PUT /{uid}/status} —— 启用 / 禁用</li>
 * </ul>
 * <p>所有写操作走 {@link AdminAuthInterceptor} 的角色拦截，VIEWER 直接被拒（7008）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 分页查询用户
     *
     * @param page      页码（1-based，默认 1）
     * @param size      每页条数（默认 20，service 层硬上限 200）
     * @param keyword   昵称 / phone / uid 关键字模糊匹配，可选
     * @param enabled   启用状态过滤，可选
     * @param loginType 登录方式过滤（WECHAT/PHONE/...），可选
     */
    @GetMapping
    public Result<PageResponse<UserSummaryDto>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "loginType", required = false) String loginType) {
        User.LoginType lt = parseLoginType(loginType);
        return Result.success(adminUserService.listUsers(page, size, keyword, enabled, lt));
    }

    /** 用户详情 */
    @GetMapping("/{uid}")
    public Result<UserDetailDto> detail(@PathVariable("uid") String uid) {
        return Result.success(adminUserService.getUserDetail(uid));
    }

    /** 启用 / 禁用用户 */
    @PutMapping("/{uid}/status")
    public Result<UserDetailDto> updateStatus(HttpServletRequest request,
                                              @PathVariable("uid") String uid,
                                              @RequestBody(required = false) UpdateUserStatusRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        return Result.success(adminUserService.updateStatus(uid, body, operator));
    }

    // ====== 内部 ======

    private User.LoginType parseLoginType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return User.LoginType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "loginType 参数不合法：" + raw);
        }
    }
}
