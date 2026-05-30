package com.lanprojects.fitcoach.admin.service;

import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.UpdateUserStatusRequest;
import com.lanprojects.fitcoach.admin.dto.UserDetailDto;
import com.lanprojects.fitcoach.admin.dto.UserSummaryDto;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.feedback.repository.UserFeedbackRepository;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 分页查询用户列表
     *
     * @param keyword   按昵称 / phone / uid 模糊匹配（OR 关系）
     * @param enabled   按启用状态过滤；null 表示不过滤
     * @param loginType 登录方式过滤；null 表示不过滤
     */
    public PageResponse<UserSummaryDto> listUsers(int page, int size, String keyword,
                                                  Boolean enabled, User.LoginType loginType) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1) - 1;  // 入参 1-based → Spring 0-based

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                ps.add(cb.or(
                        cb.like(root.get("nickname"), like),
                        cb.like(root.get("phone"), like),
                        cb.like(root.get("uid"), like)
                ));
            }
            if (enabled != null) {
                ps.add(cb.equal(root.get("enabled"), enabled));
            }
            if (loginType != null) {
                ps.add(cb.equal(root.get("loginType"), loginType));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };

        Page<User> p = userRepository.findAll(spec,
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
    public List<User> exportUsers(String keyword, Boolean enabled, User.LoginType loginType) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                ps.add(cb.or(
                        cb.like(root.get("nickname"), like),
                        cb.like(root.get("phone"), like),
                        cb.like(root.get("uid"), like)
                ));
            }
            if (enabled != null) {
                ps.add(cb.equal(root.get("enabled"), enabled));
            }
            if (loginType != null) {
                ps.add(cb.equal(root.get("loginType"), loginType));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
        Page<User> p = userRepository.findAll(spec,
                PageRequest.of(0, MAX_EXPORT_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
        return p.getContent();
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
