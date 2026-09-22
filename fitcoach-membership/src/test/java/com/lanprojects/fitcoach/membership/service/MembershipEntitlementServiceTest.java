package com.lanprojects.fitcoach.membership.service;
import com.lanprojects.fitcoach.membership.entity.MembershipEntitlement;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MembershipEntitlementServiceTest {
    private final LocalDateTime day = LocalDateTime.of(2026, 1, 1, 0, 0);
    private MembershipEntitlement grant(int days, LocalDateTime at) {
        var e = new MembershipEntitlement(); e.setDurationDays(days); e.setGrantedAt(at); return e;
    }
    @Test void refundOlderMonthPreservesYearAndGift() {
        var month = grant(30, day); var year = grant(365, day.plusDays(1)); var gift = grant(7, day.plusDays(2));
        assertEquals(day.plusDays(402), MembershipEntitlementService.calculateExpiry(List.of(month, year, gift), day));
        month.setRevoked(true);
        assertEquals(day.plusDays(373), MembershipEntitlementService.calculateExpiry(List.of(month, year, gift), day));
    }
    @Test void refundLaterOrderDoesNotExtendExpiredEarlierOrder() {
        var first = grant(7, day); var later = grant(30, day.plusDays(20)); later.setRevoked(true);
        assertEquals(day.plusDays(7), MembershipEntitlementService.calculateExpiry(List.of(first, later), day.plusDays(25)));
    }
    @Test void pendingAndRevokedOrdersContributeNothingAndLegacyRemainsExact() {
        var baseline = grant(0, day); baseline.setBaselineExpiresAt(day.plusDays(14));
        var pending = new MembershipEntitlement(); pending.setDurationDays(365);
        var refunded = grant(365, day.plusDays(1)); refunded.setRevoked(true);
        assertEquals(day.plusDays(14), MembershipEntitlementService.calculateExpiry(List.of(baseline, pending, refunded), day));
    }
}
