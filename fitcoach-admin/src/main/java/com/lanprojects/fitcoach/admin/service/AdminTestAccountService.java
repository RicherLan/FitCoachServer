package com.lanprojects.fitcoach.admin.service;

import com.lanprojects.fitcoach.admin.dto.testaccount.AdminTestAccountCreateRequest;
import com.lanprojects.fitcoach.admin.dto.testaccount.AdminTestAccountDto;
import com.lanprojects.fitcoach.admin.dto.testaccount.AdminTestAccountResetPasswordRequest;
import com.lanprojects.fitcoach.admin.dto.testaccount.AdminTestAccountUpdateRequest;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.login.service.TestLoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 后台 测试账号（{@link User.LoginType#TEST}） CRUD Service。
 *
 * <p>语义：测试账号本质是 user 表里 loginType=TEST 的一行，uid 形如 {@code test_<account>}，
 * 由 {@link TestLoginService#TEST_UID_PREFIX} 拼接。所有写操作（update/resetPassword/delete）
 * 必须先校验目标 user.loginType == TEST —— 防御性兜底，避免误把 admin 后台改测试账号的入口
 * 用到真实用户上（哪怕 id 撞了也要拒）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>列表不分页：测试账号数量级别极小（个位数 ~ 几十），按 createdAt desc 直出，前端筛选友好；</li>
 *   <li>account 重名直接 409 风格回 {@link ResultCode#ADMIN_TEST_ACCOUNT_DUPLICATE}，
 *       不返回模糊错误，方便 admin 用户快速改名；</li>
 *   <li>密码不在 update 里改 —— 必须走专门的 {@link #resetPassword} 接口，
 *       让 audit log 单独记一条 {@code RESET_TEST_ACCOUNT_PASSWORD}，回查清晰。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTestAccountService {

    /** account 仅支持英文/数字/下划线，长度 1-32，和 {@link AdminTestAccountCreateRequest} 同口径。 */
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,32}$");

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 全量列表，按 createdAt 倒序。量小，无需分页。 */
    public List<AdminTestAccountDto> list() {
        Specification<User> spec = (root, query, cb) ->
                cb.equal(root.get("loginType"), User.LoginType.TEST);
        List<User> rows = userRepository.findAll(spec,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return rows.stream().map(AdminTestAccountDto::from).toList();
    }

    /**
     * 新建测试账号。
     * <p>account 重名（uid 已存在）直接拒；nickname 缺省则自动生成 "测试账号 &lt;account&gt;"。
     */
    @Transactional
    public AdminTestAccountDto create(AdminTestAccountCreateRequest req) {
        // 防御：account 正则校验（Controller 上的 @Valid 是第一道，这里兜底防被绕过）
        if (req.getAccount() == null || !ACCOUNT_PATTERN.matcher(req.getAccount()).matches()) {
            throw new BusinessException(ResultCode.ADMIN_TEST_ACCOUNT_ACCOUNT_INVALID);
        }
        if (req.getPassword() == null
                || req.getPassword().length() < 6 || req.getPassword().length() > 64) {
            throw new BusinessException(ResultCode.ADMIN_TEST_ACCOUNT_PASSWORD_INVALID);
        }

        String account = req.getAccount();
        String uid = TestLoginService.TEST_UID_PREFIX + account;
        if (userRepository.findByUid(uid).isPresent()) {
            throw new BusinessException(ResultCode.ADMIN_TEST_ACCOUNT_DUPLICATE);
        }

        User user = new User();
        user.setUid(uid);
        user.setLoginType(User.LoginType.TEST);
        user.setNickname(req.getNickname() != null && !req.getNickname().isBlank()
                ? req.getNickname().trim()
                : "测试账号 " + account);
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setEnabled(true);
        user.setGender(0);
        // 与 DataInitializer.seedTestAccounts 保持一致：lastLoginAt 给个初值，避免列表显示空
        user.setLastLoginAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        return AdminTestAccountDto.from(saved);
    }

    /** PATCH 更新：nickname / enabled 都可空，null = 不动。 */
    @Transactional
    public AdminTestAccountDto update(Long id, AdminTestAccountUpdateRequest req) {
        User user = findTestUserOr404(id);
        if (req.getNickname() != null) {
            // 允许传空字符串清空 nickname（罕见，但语义保留给用户）
            user.setNickname(req.getNickname().isBlank() ? null : req.getNickname().trim());
        }
        if (req.getEnabled() != null) {
            user.setEnabled(req.getEnabled());
        }
        User saved = userRepository.save(user);
        return AdminTestAccountDto.from(saved);
    }

    /** 重置密码：独立接口，单独记一条 audit log。 */
    @Transactional
    public AdminTestAccountDto resetPassword(Long id, AdminTestAccountResetPasswordRequest req) {
        if (req.getPassword() == null
                || req.getPassword().length() < 6 || req.getPassword().length() > 64) {
            throw new BusinessException(ResultCode.ADMIN_TEST_ACCOUNT_PASSWORD_INVALID);
        }
        User user = findTestUserOr404(id);
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        User saved = userRepository.save(user);
        return AdminTestAccountDto.from(saved);
    }

    /**
     * 硬删除。
     * <p>注意：seed 列表里的账号（{@code test1/test2/test3}）被删后，server 重启会被
     * {@code DataInitializer.seedTestAccounts} 自动 seed 回来——这是安全网，无需特殊处理。
     */
    @Transactional
    public void delete(Long id) {
        User user = findTestUserOr404(id);
        userRepository.delete(user);
    }

    // ====== 内部 ======

    /**
     * 按 id 查测试账号 user，并校验 loginType==TEST。
     * <p>找不到 / 不是测试账号 → 统一抛 {@link ResultCode#ADMIN_USER_TARGET_NOT_FOUND}，
     * 不暴露"是被禁的真实用户"等其它信号。
     */
    private User findTestUserOr404(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.ADMIN_USER_TARGET_NOT_FOUND));
        if (user.getLoginType() != User.LoginType.TEST) {
            log.warn("admin 试图对非测试账号执行测试账号操作, id={}, loginType={}",
                    id, user.getLoginType());
            throw new BusinessException(ResultCode.ADMIN_USER_TARGET_NOT_FOUND);
        }
        return user;
    }
}
