package com.lanprojects.fitcoach.admin.service;

import com.lanprojects.fitcoach.admin.dto.CreateUserRequest;
import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.ResetUserPasswordRequest;
import com.lanprojects.fitcoach.admin.dto.UpdateUserStatusRequest;
import com.lanprojects.fitcoach.admin.dto.UserDetailDto;
import com.lanprojects.fitcoach.admin.dto.UserSummaryDto;
import com.lanprojects.fitcoach.common.client.AppFlavor;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.feedback.repository.UserFeedbackRepository;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.login.service.AccountGenerator;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 管理员侧用户管理 Service。
 * <p>
 * 支持：
 * <ul>
 *   <li>分页 + 多条件（关键字 / 启用状态 / 登录方式）查询</li>
 *   <li>详情（含该用户反馈总数）</li>
 *   <li>启用 / 禁用 — 禁用后客户端 token 校验直接拒绝（USER_DISABLED）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    /** 单页最大条数硬上限，防止误传超大 size 把 server 拖垮 */
    private static final int MAX_PAGE_SIZE = 200;

    /** P2-12：CSV 导出单次最大条数硬上限，避免一次性查 50w 用户压垮 DB / OOM */
    public static final int MAX_EXPORT_SIZE = 10_000;

    private final UserRepository userRepository;
    private final UserFeedbackRepository userFeedbackRepository;
    private final AdminUrlService adminUrlService;
    private final AccountGenerator accountGenerator;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 分页查询用户列表
     *
     * @param keyword       按昵称 / phone / uid 模糊匹配（OR 关系）
     * @param enabled       按启用状态过滤；null 表示不过滤
     * @param loginType     登录方式过滤；null 表示不过滤
     * @param flavor        注册 flavor 过滤（阶段 6 波 1）；null 且 flavorIsNull=false 表示不过滤
     * @param flavorIsNull  显式筛选 register_flavor 为 NULL 的历史用户（Admin 前端 flavor=UNKNOWN 映射到此）
     */
    public PageResponse<UserSummaryDto> listUsers(int page, int size, String keyword,
                                                  Boolean enabled, User.LoginType loginType,
                                                  AppFlavor flavor, boolean flavorIsNull) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1) - 1;  // 入参 1-based → Spring 0-based

        Page<User> p = userRepository.findAll(buildSpec(keyword, enabled, loginType, flavor, flavorIsNull),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(p, u -> UserSummaryDto.from(
                u, adminUrlService.resolve(u.getAvatarUrl()), maskPhone(u.getPhone())));
    }

    /**
     * P2-12：按筛选条件导出用户（CSV）。
     * <p>复用 {@link #listUsers} 的同款 Spec，最多返回 {@link #MAX_EXPORT_SIZE} 条，
     * 超过上限的部分由调用方提示用户继续按更细条件筛选后再导。
     * <p>返回 entity 而非 DTO —— 让 controller 决定脱敏 / 字段顺序 / 表头文案。
     */
    public List<User> exportUsers(String keyword, Boolean enabled, User.LoginType loginType,
                                  AppFlavor flavor, boolean flavorIsNull) {
        Page<User> p = userRepository.findAll(buildSpec(keyword, enabled, loginType, flavor, flavorIsNull),
                PageRequest.of(0, MAX_EXPORT_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
        return p.getContent();
    }

    /**
     * 列表 / 导出共用的 Spec：keyword 对 nickname/phone/uid/account 做模糊匹配。
     *
     * <p><b>flavor 三态语义</b>（阶段 6 波 1）：
     * <ol>
     *   <li>{@code flavorIsNull=false, flavor=null} → 不过滤（全量）；</li>
     *   <li>{@code flavorIsNull=false, flavor=CN/GLOBAL} → EQUAL 过滤；</li>
     *   <li>{@code flavorIsNull=true}                     → IS NULL 过滤（"未标注 flavor"的老用户）。</li>
     * </ol>
     */
    private Specification<User> buildSpec(String keyword, Boolean enabled, User.LoginType loginType,
                                          AppFlavor flavor, boolean flavorIsNull) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                ps.add(cb.or(
                        cb.like(root.get("nickname"), like),
                        cb.like(root.get("phone"), like),
                        cb.like(root.get("uid"), like),
                        cb.like(root.get("account"), like)
                ));
            }
            if (enabled != null) {
                ps.add(cb.equal(root.get("enabled"), enabled));
            }
            if (loginType != null) {
                ps.add(cb.equal(root.get("loginType"), loginType));
            }
            if (flavorIsNull) {
                ps.add(cb.isNull(root.get("registerFlavor")));
            } else if (flavor != null) {
                ps.add(cb.equal(root.get("registerFlavor"), flavor));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /**
     * admin 后台手动创建 C 端用户。
     *
     * <p>用途：运营 / 客服内部账号、QA 测试账号。
     * <p>语义：
     * <ul>
     *   <li>{@code account} 由 {@link AccountGenerator} 生成，与微信 / 手机号注册路径完全一致；</li>
     *   <li>{@code registrationSource = ADMIN_CREATED}，与普通用户区分；</li>
     *   <li>{@code loginType = ACCOUNT}，表示该账号的首选登录方式是账号 + 密码；</li>
     *   <li>{@code passwordHash} 由 BCrypt 哈希后写入。</li>
     * </ul>
     */
    @Transactional
    public UserDetailDto createUser(CreateUserRequest req, String operator) {
        if (req == null || req.getNickname() == null || req.getPassword() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "昵称和密码不能为空");
        }

        // account：admin 指定则用指定值（先查重），否则自动生成
        String account;
        if (req.getAccount() != null && !req.getAccount().isBlank()) {
            account = req.getAccount().trim();
            if (userRepository.existsByAccount(account)) {
                throw new BusinessException(ResultCode.ADMIN_USER_ACCOUNT_DUPLICATE,
                        "账号 " + account + " 已被占用");
            }
        } else {
            account = accountGenerator.generateUnique();
        }

        User user = new User();
        user.setUid(UUID.randomUUID().toString().replace("-", ""));
        user.setAccount(account);
        user.setNickname(req.getNickname().trim());
        user.setLoginType(User.LoginType.ACCOUNT);
        user.setRegistrationSource(User.RegistrationSource.ADMIN_CREATED);
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setEnabled(true);
        user.setGender(0);
        user.setLastLoginAt(LocalDateTime.now());
        try {
            User saved = userRepository.save(user);
            log.info("[admin] {} 创建用户, uid={}, account={}",
                    operator, saved.getUid(), saved.getAccount());
            long feedbackCount = userFeedbackRepository.countByUid(saved.getUid());
            return UserDetailDto.from(saved, adminUrlService.resolve(saved.getAvatarUrl()), feedbackCount);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 极小概率：AccountGenerator 之后并发再被抢占；回 ACCOUNT_DUPLICATE 让 admin 重试一次
            log.warn("[admin] 创建用户冲突（疑似 account 唯一索引冲突）, operator={}, msg={}",
                    operator, e.getMessage());
            throw new BusinessException(ResultCode.ADMIN_USER_ACCOUNT_DUPLICATE);
        }
    }

    /**
     * admin 后台重置用户密码 —— 覆盖 {@code passwordHash}，独立 audit 记录。
     * <p>不影响 user 当前的 token（暂未实现 token 黑名单；如需立即踢登录，可配合启停操作）。
     */
    @Transactional
    public UserDetailDto resetPassword(String uid, ResetUserPasswordRequest req, String operator) {
        if (req == null || req.getPassword() == null
                || req.getPassword().length() < 6 || req.getPassword().length() > 64) {
            throw new BusinessException(ResultCode.ADMIN_USER_PASSWORD_INVALID);
        }
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.ADMIN_USER_TARGET_NOT_FOUND));
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        userRepository.save(user);
        log.info("[admin] {} 重置用户密码, uid={}", operator, uid);
        long feedbackCount = userFeedbackRepository.countByUid(uid);
        return UserDetailDto.from(user, adminUrlService.resolve(user.getAvatarUrl()), feedbackCount);
    }

    /** 用户详情 */
    public UserDetailDto getUserDetail(String uid) {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.ADMIN_USER_TARGET_NOT_FOUND));
        long feedbackCount = userFeedbackRepository.countByUid(uid);
        return UserDetailDto.from(user, adminUrlService.resolve(user.getAvatarUrl()), feedbackCount);
    }

    /** 启用 / 禁用用户 */
    @Transactional
    public UserDetailDto updateStatus(String uid, UpdateUserStatusRequest req, String operator) {
        if (req == null || req.getEnabled() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "缺少 enabled 字段");
        }
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new BusinessException(ResultCode.ADMIN_USER_TARGET_NOT_FOUND));
        Boolean before = user.getEnabled();
        user.setEnabled(req.getEnabled());
        userRepository.save(user);
        log.info("管理员变更用户状态, operator={}, uid={}, before={}, after={}",
                operator, uid, before, req.getEnabled());
        long feedbackCount = userFeedbackRepository.countByUid(uid);
        return UserDetailDto.from(user, adminUrlService.resolve(user.getAvatarUrl()), feedbackCount);
    }

    // ====== 内部 ======

    /** 列表里展示用脱敏手机号：1 3 8 ****1234 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
