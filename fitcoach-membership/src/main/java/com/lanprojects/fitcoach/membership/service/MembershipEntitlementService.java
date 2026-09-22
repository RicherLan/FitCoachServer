package com.lanprojects.fitcoach.membership.service;

import com.lanprojects.fitcoach.membership.entity.*;
import com.lanprojects.fitcoach.membership.repository.*;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class MembershipEntitlementService {
    private final MembershipEntitlementRepository ledger;
    private final UserMembershipRepository memberships;
    private final UserRepository users;

    private void lock(Long uid) {
        users.findLockedById(uid).orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_OWNED));
    }
    private void preserveLegacy(Long uid) {
        var entries = ledger.findByUserIdOrderByGrantedAtAscIdAsc(uid);
        if (entries.stream().anyMatch(e -> e.getGrantedAt() != null || e.getBaselineExpiresAt() != null)) { return; }
        memberships.findByUserId(uid).ifPresent(m -> {
            var baseline = new MembershipEntitlement(); baseline.setOrderId("LEGACY:" + uid);
            baseline.setUserId(uid); baseline.setPlanId(m.getPlanId()); baseline.setPlanCode(m.getPlanCode());
            baseline.setGrantedAt(m.getActivatedAt()); baseline.setBaselineExpiresAt(m.getExpiresAt());
            ledger.saveAndFlush(baseline);
        });
    }
    private MembershipEntitlement snapshot(Long uid, MembershipPlan plan, int days, String orderId) {
        var e = new MembershipEntitlement(); e.setOrderId(orderId); e.setUserId(uid);
        e.setPlanId(plan.getId()); e.setPlanCode(plan.getPlanCode()); e.setDurationDays(days);
        return e;
    }
    /** 与下单同一事务保存天数快照，后续改套餐不影响已下单权益。 */
    @Transactional
    public void reserve(Long uid, MembershipPlan plan, String orderId) {
        lock(uid); preserveLegacy(uid);
        if (ledger.findByOrderId(orderId).isEmpty()) {
            ledger.save(snapshot(uid, plan, plan.getDurationDays(), orderId));
        }
    }
    @Transactional
    public UserMembership grant(Long uid, MembershipPlan plan, int days, String orderId) {
        lock(uid); preserveLegacy(uid);
        String key = orderId == null ? "GIFT:" + UUID.randomUUID() : orderId;
        var entry = ledger.findByOrderId(key).orElseGet(() -> snapshot(uid, plan, days, key));
        if (!entry.getUserId().equals(uid)) { throw new BusinessException(ResultCode.PAYMENT_ORDER_NOT_OWNED); }
        // 退款先于异步激活抵达时，revoked 墓碑阻止重新发放。
        if (!entry.isRevoked() && entry.getGrantedAt() == null) {
            entry.setGrantedAt(LocalDateTime.now()); ledger.saveAndFlush(entry);
        }
        var member = memberships.findByUserId(uid).orElseGet(() -> {
            var m = new UserMembership(); m.setUserId(uid); m.setPlanId(plan.getId());
            m.setPlanCode(plan.getPlanCode()); m.setActivatedAt(LocalDateTime.now()); return m;
        });
        return recalculate(uid, member);
    }
    @Transactional
    public void refund(Long uid, String orderId) {
        lock(uid); preserveLegacy(uid);
        var entry = ledger.findByOrderId(orderId).orElseGet(() -> {
            var tombstone = new MembershipEntitlement(); tombstone.setOrderId(orderId); tombstone.setUserId(uid);
            log.warn("[membership] 退款无订单权益快照，保留历史基线待人工核对 userId={} orderId={}", uid, orderId);
            return tombstone;
        });
        if (!entry.getUserId().equals(uid)) { throw new BusinessException(ResultCode.PAYMENT_ORDER_NOT_OWNED); }
        entry.setRevoked(true); ledger.saveAndFlush(entry);
        memberships.findByUserId(uid).ifPresent(m -> recalculate(uid, m));
    }
    @Transactional
    public void revokeAll(Long uid) {
        lock(uid); preserveLegacy(uid);
        var entries = ledger.findByUserIdOrderByGrantedAtAscIdAsc(uid);
        entries.forEach(e -> e.setRevoked(true)); ledger.saveAllAndFlush(entries);
        memberships.findByUserId(uid).ifPresent(m -> recalculate(uid, m));
    }
    private UserMembership recalculate(Long uid, UserMembership member) {
        var entries = ledger.findByUserIdOrderByGrantedAtAscIdAsc(uid);
        member.setExpiresAt(calculateExpiry(entries, LocalDateTime.now()));
        entries.stream().filter(e -> !e.isRevoked() && e.getGrantedAt() != null && e.getPlanId() != null)
                .reduce((a, b) -> b).ifPresent(e -> {
                    member.setPlanId(e.getPlanId()); member.setPlanCode(e.getPlanCode()); member.setLastOrderId(e.getOrderId());
                });
        return memberships.save(member);
    }
    /** 保留每笔的实际开通时刻，移除退款贡献后重放剩余时长。 */
    static LocalDateTime calculateExpiry(List<MembershipEntitlement> entries, LocalDateTime now) {
        LocalDateTime cursor = null;
        for (var e : entries.stream().filter(e -> !e.isRevoked() && e.getGrantedAt() != null)
                .sorted(Comparator.comparing(MembershipEntitlement::getGrantedAt)).toList()) {
            if (e.getBaselineExpiresAt() != null) {
                if (cursor == null || cursor.isBefore(e.getBaselineExpiresAt())) { cursor = e.getBaselineExpiresAt(); }
            } else {
                if (cursor == null || cursor.isBefore(e.getGrantedAt())) { cursor = e.getGrantedAt(); }
                cursor = cursor.plusDays(e.getDurationDays());
            }
        }
        return cursor == null ? now.minusSeconds(1) : cursor;
    }
}
