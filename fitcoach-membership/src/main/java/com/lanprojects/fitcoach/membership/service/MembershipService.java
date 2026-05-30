package com.lanprojects.fitcoach.membership.service;

import com.lanprojects.fitcoach.common.event.PaymentSucceededEvent;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.membership.entity.MembershipActivationFailure;
import com.lanprojects.fitcoach.membership.entity.MembershipActivationFailure.Status;
import com.lanprojects.fitcoach.membership.entity.MembershipPlan;
import com.lanprojects.fitcoach.membership.entity.UserMembership;
import com.lanprojects.fitcoach.membership.repository.MembershipActivationFailureRepository;
import com.lanprojects.fitcoach.membership.repository.MembershipPlanRepository;
import com.lanprojects.fitcoach.membership.repository.UserMembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 会员核心服务 — 提供：
 * <ol>
 *   <li>查询用户当前会员状态（高频，几乎每次客户端启动都会调）；</li>
 *   <li>检查用户是否拥有某项特权（业务方调用）；</li>
 *   <li>套餐查询（用户端列表 + admin 端 CRUD）；</li>
 *   <li>监听 {@link PaymentSucceededEvent} 自动激活/续费会员；</li>
 *   <li>Admin 操作：手动赠送 / 延长 / 撤销 会员。</li>
 * </ol>
 *
 * <p><b>"激活会员"逻辑</b>：
 * <ul>
 *   <li>首次开通：插入新行，activatedAt = now，expiresAt = now + durationDays；</li>
 *   <li>续费时（已存在记录且未过期）：expiresAt = currentExpiresAt + durationDays（叠加，不亏天数）；</li>
 *   <li>续费时（已存在记录但已过期）：activatedAt = now（视为重新开通），expiresAt = now + durationDays。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipPlanRepository planRepository;
    private final UserMembershipRepository membershipRepository;
    private final MembershipActivationFailureRepository activationFailureRepository;

    /** 首次失败后多久重试（毫秒） — 后续重试按指数退避 */
    private static final long INITIAL_BACKOFF_MS = 60_000L; // 1 分钟
    private static final long MAX_BACKOFF_MS = 3600_000L;   // 1 小时上限
    /** 超过该次数标记 PERMANENT_FAIL — 留给人工 */
    public static final int MAX_RETRY_COUNT = 10;

    // ====== 会员状态查询 ======

    /** 拿当前用户的会员记录（可能为 empty 表示从未开通过） */
    public Optional<UserMembership> findByUserId(Long userId) {
        return membershipRepository.findByUserId(userId);
    }

    /**
     * 批量按 userId 拉会员记录，返回 {userId → UserMembership}。
     * <p>列表场景（如 admin 批量查会员状态）专用，避免逐条 select 引发 N+1。
     * <p>没有会员记录的 userId 不会出现在结果 map 中。
     */
    public java.util.Map<Long, UserMembership> findMembershipsByUserIds(java.util.Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return membershipRepository.findByUserIdIn(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(UserMembership::getUserId, m -> m));
    }

    /**
     * 批量按 planCode 拉套餐，返回 {planCode → MembershipPlan}。
     * <p>列表场景专用，避免循环 findPlanByCode 引发 N+1。
     * <p>不存在的 planCode 不会出现在结果 map 中。
     */
    public java.util.Map<String, MembershipPlan> findPlansByCodes(java.util.Collection<String> planCodes) {
        if (planCodes == null || planCodes.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return planRepository.findByPlanCodeIn(planCodes).stream()
                .collect(java.util.stream.Collectors.toMap(MembershipPlan::getPlanCode, p -> p));
    }

    /**
     * 是否当前生效（now < expiresAt）。从未开通过 / 已过期 都返回 false。
     */
    public boolean isActive(Long userId) {
        return membershipRepository.findByUserId(userId)
                .map(UserMembership::isActive)
                .orElse(false);
    }

    /**
     * 强制要求会员，否则抛 8001。业务方在「调付费动作能力」前调。
     */
    public void requireMembership(Long userId) {
        if (!isActive(userId)) {
            throw new BusinessException(ResultCode.MEMBERSHIP_REQUIRED);
        }
    }

    /**
     * 是否拥有某项特权。MVP 阶段：只要是有效会员就拥有所有 enum 值列出的特权。
     * <p>未来要"分套餐授予特权"时，在此处按 plan_code 查 privilegeJson 即可，调用方代码不变。
     */
    public boolean hasPrivilege(Long userId, MembershipPrivilege privilege) {
        // MVP: 有效会员 = 拥有所有特权
        return isActive(userId);
    }

    // ====== 套餐查询 ======

    /** 客户端套餐列表（仅启用） */
    public List<MembershipPlan> listEnabledPlans() {
        return planRepository.findByEnabledTrueOrderBySortOrderAsc();
    }

    /** Admin 套餐列表（含禁用） */
    public List<MembershipPlan> listAllPlans() {
        return planRepository.findAllByOrderBySortOrderAsc();
    }

    public MembershipPlan findPlanByCode(String planCode) {
        return planRepository.findByPlanCode(planCode)
                .orElseThrow(() -> new BusinessException(ResultCode.MEMBERSHIP_PLAN_NOT_FOUND));
    }

    public MembershipPlan findEnabledPlanByCode(String planCode) {
        MembershipPlan plan = findPlanByCode(planCode);
        if (Boolean.FALSE.equals(plan.getEnabled())) {
            throw new BusinessException(ResultCode.MEMBERSHIP_PLAN_DISABLED);
        }
        return plan;
    }

    // ====== 会员激活（事件监听 + Admin 直接调） ======

    /**
     * 监听支付成功事件，自动激活/续费会员。
     *
     * <p><b>关键设计</b>：
     * <ul>
     *   <li>{@code AFTER_COMMIT}：保证只有 payment 端订单状态成功 commit 才会激活会员，
     *       避免 payment 事务回滚但会员已激活的"幻读式"问题；</li>
     *   <li>{@code @Async}：避免拖慢支付主链路 / 微信回调响应时间；</li>
     *   <li>方法内 try/catch 兜底：监听器异常不会传到事件发布者，避免影响 payment 事务。</li>
     * </ul>
     *
     * <p><b>幂等性</b>：同一 orderId 可能因网络重试、补偿任务等被重复处理。
     * 我们通过比较 user_membership.last_order_id == event.orderId 来短路重复事件。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, classes = PaymentSucceededEvent.class)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        try {
            log.info("[membership] 收到支付成功事件 orderId={} userId={} planCode={} channel={}",
                    event.getOrderId(), event.getUserId(), event.getPlanCode(), event.getChannel());
            activate(event.getUserId(), event.getPlanCode(), event.getOrderId());
        } catch (Exception e) {
            // 监听器异常不能扩散：扩散会让 Spring 误判事件传播失败，但 payment 事务已经 commit
            // 如果激活失败 → 写入失败记录表，由 MembershipActivationRetryJob 周期重试，避免静默丢失资损
            log.error("[membership] 激活会员失败，已记录待补偿 orderId={} userId={} planCode={}",
                    event.getOrderId(), event.getUserId(), event.getPlanCode(), e);
            try {
                recordActivationFailure(event, e);
            } catch (Exception persistEx) {
                // 兜底再兜底：连失败表都写不进去 — 只能依赖 P0-2 的 MembershipReconcileJob 扫订单兜底
                log.error("[membership] 写入 activation_failure 记录也失败 orderId={}",
                        event.getOrderId(), persistEx);
            }
        }
    }

    /**
     * 把激活失败的事件落地到 {@code membership_activation_failure}，供 retry job 周期重试。
     * <p>单独开事务（{@code REQUIRES_NEW}）：当前监听器无事务上下文（@Async 切线程），
     * 但插入失败记录这个写操作本身需要事务保证。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordActivationFailure(PaymentSucceededEvent event, Throwable cause) {
        String reason = truncate(cause == null ? "unknown" : cause.toString(), 1024);
        // 同一 orderId 可能多次落入此分支（事件重投/补偿任务并行），upsert 一下
        MembershipActivationFailure failure = activationFailureRepository
                .findByOrderId(event.getOrderId())
                .orElseGet(MembershipActivationFailure::new);

        if (failure.getId() == null) {
            // 新记录
            failure.setOrderId(event.getOrderId());
            failure.setUserId(event.getUserId());
            failure.setPlanCode(event.getPlanCode());
            failure.setStatus(Status.PENDING);
            failure.setRetryCount(1);
            failure.setNextRetryAt(LocalDateTime.now().plusNanos(INITIAL_BACKOFF_MS * 1_000_000L));
        } else if (failure.getStatus() == Status.PENDING) {
            // 重复失败 — 增量并按指数退避推后下次重试
            failure.setRetryCount(failure.getRetryCount() + 1);
            failure.setNextRetryAt(LocalDateTime.now().plusNanos(
                    computeBackoffMs(failure.getRetryCount()) * 1_000_000L));
            if (failure.getRetryCount() >= MAX_RETRY_COUNT) {
                failure.setStatus(Status.PERMANENT_FAIL);
                log.error("[membership] 激活失败重试 {} 次仍未成功，标记 PERMANENT_FAIL orderId={}",
                        MAX_RETRY_COUNT, event.getOrderId());
            }
        }
        failure.setLastFailReason(reason);
        activationFailureRepository.save(failure);
    }

    /**
     * 由 retry job 调用 — 尝试重新激活并更新 failure 记录状态。
     * <p>成功 → status=SUCCESS + resolvedAt；失败 → 走 {@link #recordActivationFailure} 同一指数退避逻辑。
     */
    public void retryActivation(MembershipActivationFailure failure) {
        try {
            activate(failure.getUserId(), failure.getPlanCode(), failure.getOrderId());
            markActivationFailureResolved(failure.getId());
            log.info("[membership] 补偿激活成功 orderId={} userId={} (第 {} 次尝试)",
                    failure.getOrderId(), failure.getUserId(), failure.getRetryCount() + 1);
        } catch (Exception e) {
            log.warn("[membership] 补偿激活失败 orderId={} retryCount={} reason={}",
                    failure.getOrderId(), failure.getRetryCount(), e.toString());
            // 复用 record 逻辑做退避 / PERMANENT_FAIL 转换
            PaymentSucceededEvent pseudoEvent = PaymentSucceededEvent.builder()
                    .orderId(failure.getOrderId())
                    .userId(failure.getUserId())
                    .planCode(failure.getPlanCode())
                    .build();
            try {
                recordActivationFailure(pseudoEvent, e);
            } catch (Exception persistEx) {
                log.error("[membership] 更新 activation_failure 记录失败 orderId={}",
                        failure.getOrderId(), persistEx);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markActivationFailureResolved(Long failureId) {
        activationFailureRepository.findById(failureId).ifPresent(f -> {
            f.setStatus(Status.SUCCESS);
            f.setResolvedAt(LocalDateTime.now());
            activationFailureRepository.save(f);
        });
    }

    /** 指数退避：1min, 2min, 4min, ..., 上限 60min */
    private long computeBackoffMs(int retryCount) {
        long backoff = INITIAL_BACKOFF_MS * (1L << Math.min(retryCount - 1, 10));
        return Math.min(backoff, MAX_BACKOFF_MS);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    /**
     * 激活/续费会员（事件监听器 + Admin 操作 + 测试用）。
     * <p>幂等：同一 orderId 重复调用只生效第一次。
     */
    @Transactional
    public UserMembership activate(Long userId, String planCode, String orderId) {
        MembershipPlan plan = findPlanByCode(planCode);
        // 注意：plan.enabled=false（已停售）时，仍然允许激活——已购订单不能因为套餐下架就拒绝发货
        return upsertMembership(userId, plan, plan.getDurationDays(), orderId);
    }

    /**
     * Admin 操作：手动赠送指定天数的会员（不依赖 plan_code，但仍需挂一个 plan_code 以便统一查询，
     * 默认挂 "GIFT" / 实际从参数指定）。
     */
    @Transactional
    public UserMembership grantDays(Long userId, String planCode, int extraDays, String operatorOrderId) {
        if (extraDays <= 0) {
            throw new BusinessException(ResultCode.MEMBERSHIP_GRANT_DAYS_INVALID);
        }
        MembershipPlan plan = findPlanByCode(planCode);
        return upsertMembership(userId, plan, extraDays, operatorOrderId);
    }

    /**
     * Admin 操作：撤销会员（立即失效）
     */
    @Transactional
    public void revoke(Long userId) {
        membershipRepository.findByUserId(userId).ifPresent(m -> {
            m.setExpiresAt(LocalDateTime.now().minusSeconds(1));
            membershipRepository.save(m);
            log.info("[membership] 撤销会员 userId={} 原 expiresAt={}", userId, m.getExpiresAt());
        });
    }

    // ====== Admin 套餐 CRUD ======

    /** 创建套餐。校验 planCode 唯一。 */
    @Transactional
    public MembershipPlan createPlan(MembershipPlan toCreate) {
        if (toCreate.getPlanCode() == null || toCreate.getPlanCode().isBlank()) {
            throw new BusinessException(ResultCode.MEMBERSHIP_PLAN_CODE_INVALID);
        }
        planRepository.findByPlanCode(toCreate.getPlanCode()).ifPresent(p -> {
            throw new BusinessException(ResultCode.MEMBERSHIP_PLAN_CODE_DUPLICATE);
        });
        toCreate.setId(null);
        if (toCreate.getEnabled() == null) toCreate.setEnabled(true);
        if (toCreate.getSortOrder() == null) toCreate.setSortOrder(0);
        MembershipPlan saved = planRepository.save(toCreate);
        log.info("[membership] 创建套餐 id={} planCode={} priceCny={}",
                saved.getId(), saved.getPlanCode(), saved.getPriceCny());
        return saved;
    }

    /**
     * 更新套餐。**禁止改 planCode**（一旦发布会破坏订单 / user_membership 的关联），其它字段允许改。
     * <p>对 priceUsdCents / applePriceTier / appleProductId / googleProductId 这几个海外字段：
     * 传 null = 不动；传 -1 / 空串 = 清空。
     */
    @Transactional
    public MembershipPlan updatePlan(Long id, MembershipPlan patch) {
        MembershipPlan existing = planRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.MEMBERSHIP_PLAN_NOT_FOUND));
        if (patch.getDisplayName() != null) existing.setDisplayName(patch.getDisplayName());
        if (patch.getDurationDays() != null) existing.setDurationDays(patch.getDurationDays());
        if (patch.getPriceCny() != null) existing.setPriceCny(patch.getPriceCny());
        if (patch.getPriceUsdCents() != null) {
            existing.setPriceUsdCents(patch.getPriceUsdCents() < 0 ? null : patch.getPriceUsdCents());
        }
        if (patch.getApplePriceTier() != null) {
            existing.setApplePriceTier(patch.getApplePriceTier() < 0 ? null : patch.getApplePriceTier());
        }
        if (patch.getAppleProductId() != null) {
            existing.setAppleProductId(patch.getAppleProductId().isBlank() ? null : patch.getAppleProductId());
        }
        if (patch.getGoogleProductId() != null) {
            existing.setGoogleProductId(patch.getGoogleProductId().isBlank() ? null : patch.getGoogleProductId());
        }
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getSortOrder() != null) existing.setSortOrder(patch.getSortOrder());
        if (patch.getEnabled() != null) existing.setEnabled(patch.getEnabled());

        MembershipPlan saved = planRepository.save(existing);
        log.info("[membership] 更新套餐 id={} planCode={} enabled={}",
                saved.getId(), saved.getPlanCode(), saved.getEnabled());
        return saved;
    }

    /** 拿单个套餐（admin 编辑表单用），找不到抛 8002。 */
    public MembershipPlan getPlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.MEMBERSHIP_PLAN_NOT_FOUND));
    }

    // ====== 内部 ======

    /**
     * 续费/首次开通的统一逻辑：
     * <ul>
     *   <li>未开通 / 已过期 → activatedAt = now, expiresAt = now + days</li>
     *   <li>未过期续费 → expiresAt += days（不动 activatedAt，保留首次开通时间）</li>
     * </ul>
     * <p>幂等：相同 orderId 重复调用只激活一次（短路返回现有记录）。
     */
    private UserMembership upsertMembership(Long userId, MembershipPlan plan, int days, String orderId) {
        Optional<UserMembership> existing = membershipRepository.findByUserId(userId);

        if (existing.isPresent() && orderId != null && orderId.equals(existing.get().getLastOrderId())) {
            log.info("[membership] 幂等：orderId={} 已激活过，跳过 userId={}", orderId, userId);
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.now();
        UserMembership membership = existing.orElseGet(UserMembership::new);

        if (existing.isEmpty() || !existing.get().isActive()) {
            // 首次开通 / 重新开通（已过期）
            membership.setActivatedAt(now);
            membership.setExpiresAt(now.plusDays(days));
        } else {
            // 续费：从原到期时间往后叠加
            membership.setExpiresAt(existing.get().getExpiresAt().plusDays(days));
        }
        membership.setUserId(userId);
        membership.setPlanId(plan.getId());
        membership.setPlanCode(plan.getPlanCode());
        membership.setLastOrderId(orderId);
        // autoRenewEnabled 保持现状（MVP 始终 false）

        UserMembership saved = membershipRepository.save(membership);
        log.info("[membership] 激活/续费 userId={} planCode={} expiresAt={} orderId={}",
                userId, plan.getPlanCode(), saved.getExpiresAt(), orderId);
        return saved;
    }
}
