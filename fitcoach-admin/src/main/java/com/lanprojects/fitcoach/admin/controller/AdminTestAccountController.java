package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.testaccount.AdminTestAccountCreateRequest;
import com.lanprojects.fitcoach.admin.dto.testaccount.AdminTestAccountDto;
import com.lanprojects.fitcoach.admin.dto.testaccount.AdminTestAccountResetPasswordRequest;
import com.lanprojects.fitcoach.admin.dto.testaccount.AdminTestAccountUpdateRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.admin.service.AdminTestAccountService;
import com.lanprojects.fitcoach.common.model.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台 — 测试账号管理（{@code user.loginType=TEST}）。
 *
 * <p>路径前缀：{@code /api/admin/test-accounts}
 * <ul>
 *   <li>{@code GET /} —— 全量列表，按 createdAt 倒序（量小，无需分页）</li>
 *   <li>{@code POST /} —— 新建（account 重名 → 7202）</li>
 *   <li>{@code PATCH /{id}} —— 编辑昵称 / 启停（PATCH 语义；null = 不动）</li>
 *   <li>{@code POST /{id}/reset-password} —— 重置密码（独立路径，单独留 audit 痕迹）</li>
 *   <li>{@code DELETE /{id}} —— 硬删（seed 列表里的账号 server 重启会自动 seed 回来）</li>
 * </ul>
 *
 * <p><b>鉴权</b>：本路径走 {@link AdminAuthInterceptor}，VIEWER 角色只能 GET，
 * 写操作（POST/PATCH/DELETE）会被 {@code role.canWrite()} 校验自动 403。
 *
 * <p>配套的客户端登录入口：{@code POST /api/auth/login/test}（{@code TestLoginController}）
 * + 服务端开关 {@code test_login.enabled}（admin → 系统配置 → test_login 分组）。
 */
@Slf4j
@Tag(name = "后台-测试账号管理", description = "CRUD + 重置密码 + 启停（仅作用于 loginType=TEST 行）")
@RestController
@RequestMapping("/api/admin/test-accounts")
@RequiredArgsConstructor
public class AdminTestAccountController {

    private final AdminTestAccountService testAccountService;
    private final AdminAuditLogService auditLogService;

    /** 列表（无分页） */
    @GetMapping
    public Result<List<AdminTestAccountDto>> list() {
        return Result.success(testAccountService.list());
    }

    /** 新建 */
    @PostMapping
    public Result<AdminTestAccountDto> create(
            HttpServletRequest request,
            @Valid @RequestBody AdminTestAccountCreateRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            AdminTestAccountDto dto = testAccountService.create(body);
            log.info("[admin] {} 创建测试账号 id={} account={} uid={}",
                    operator, dto.getId(), dto.getAccount(), dto.getUid());
            auditLogService.logSuccess(request, AdminAuditAction.CREATE_TEST_ACCOUNT,
                    "USER", String.valueOf(dto.getId()),
                    String.format("account=%s, uid=%s, nickname=%s",
                            dto.getAccount(), dto.getUid(), dto.getNickname()));
            return Result.success(dto);
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.CREATE_TEST_ACCOUNT,
                    "USER", null,
                    String.format("account=%s", body.getAccount()),
                    e.getMessage());
            throw e;
        }
    }

    /** PATCH 更新（nickname / enabled） */
    @PatchMapping("/{id}")
    public Result<AdminTestAccountDto> update(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminTestAccountUpdateRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            AdminTestAccountDto dto = testAccountService.update(id, body);
            log.info("[admin] {} 更新测试账号 id={} uid={} nickname={} enabled={}",
                    operator, dto.getId(), dto.getUid(), dto.getNickname(), dto.getEnabled());
            auditLogService.logSuccess(request, AdminAuditAction.UPDATE_TEST_ACCOUNT,
                    "USER", String.valueOf(id),
                    String.format("uid=%s, nickname=%s, enabled=%s",
                            dto.getUid(), dto.getNickname(), dto.getEnabled()));
            return Result.success(dto);
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.UPDATE_TEST_ACCOUNT,
                    "USER", String.valueOf(id),
                    String.format("nickname=%s, enabled=%s", body.getNickname(), body.getEnabled()),
                    e.getMessage());
            throw e;
        }
    }

    /** 重置密码（独立接口） */
    @PostMapping("/{id}/reset-password")
    public Result<AdminTestAccountDto> resetPassword(
            HttpServletRequest request,
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminTestAccountResetPasswordRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            AdminTestAccountDto dto = testAccountService.resetPassword(id, body);
            log.info("[admin] {} 重置测试账号密码 id={} uid={}", operator, dto.getId(), dto.getUid());
            // 注意：summary 不要带密码明文 / 哈希，仅留账号定位信息
            auditLogService.logSuccess(request, AdminAuditAction.RESET_TEST_ACCOUNT_PASSWORD,
                    "USER", String.valueOf(id),
                    String.format("uid=%s, account=%s", dto.getUid(), dto.getAccount()));
            return Result.success(dto);
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.RESET_TEST_ACCOUNT_PASSWORD,
                    "USER", String.valueOf(id),
                    "reset password",
                    e.getMessage());
            throw e;
        }
    }

    /** 硬删 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable("id") Long id) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        try {
            testAccountService.delete(id);
            log.info("[admin] {} 删除测试账号 id={}", operator, id);
            auditLogService.logSuccess(request, AdminAuditAction.DELETE_TEST_ACCOUNT,
                    "USER", String.valueOf(id), "hard delete");
            return Result.success(null);
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.DELETE_TEST_ACCOUNT,
                    "USER", String.valueOf(id), "hard delete", e.getMessage());
            throw e;
        }
    }
}
