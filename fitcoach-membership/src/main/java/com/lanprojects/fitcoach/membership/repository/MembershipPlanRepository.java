package com.lanprojects.fitcoach.membership.repository;

import com.lanprojects.fitcoach.membership.entity.MembershipPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {

    Optional<MembershipPlan> findByPlanCode(String planCode);

    /** 客户端列表：只返回上架的，按 sortOrder 升序 */
    List<MembershipPlan> findByEnabledTrueOrderBySortOrderAsc();

    /** Admin 列表：全部，按 sortOrder 升序 */
    List<MembershipPlan> findAllByOrderBySortOrderAsc();
}
