package com.lanprojects.fitcoach.membership.repository;
import com.lanprojects.fitcoach.membership.entity.MembershipEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface MembershipEntitlementRepository extends JpaRepository<MembershipEntitlement, Long> {
    Optional<MembershipEntitlement> findByOrderId(String orderId);
    List<MembershipEntitlement> findByUserIdOrderByGrantedAtAscIdAsc(Long userId);
}
