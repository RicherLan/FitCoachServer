package com.lanprojects.fitcoach.membership.repository;

import com.lanprojects.fitcoach.membership.entity.MembershipActivationFailure;
import com.lanprojects.fitcoach.membership.entity.MembershipActivationFailure.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MembershipActivationFailureRepository
        extends JpaRepository<MembershipActivationFailure, Long> {

    Optional<MembershipActivationFailure> findByOrderId(String orderId);

    /**
     * 取出 status=PENDING 且 next_retry_at <= now 的待重试记录，按时间正序，限制最多 {@code limit} 条。
     * <p>使用原生 SQL LIMIT 而非 {@code Pageable}，单次扫描足够轻；如未来需要分页，再换。
     */
    @Query(value = "SELECT * FROM membership_activation_failure " +
            "WHERE status = 'PENDING' AND next_retry_at <= :now " +
            "ORDER BY next_retry_at ASC LIMIT :limit", nativeQuery = true)
    List<MembershipActivationFailure> findReadyForRetry(@Param("now") LocalDateTime now,
                                                         @Param("limit") int limit);

    long countByStatus(Status status);
}
