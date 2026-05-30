package com.lanprojects.fitcoach.membership.repository;

import com.lanprojects.fitcoach.membership.entity.UserMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserMembershipRepository extends JpaRepository<UserMembership, Long> {

    Optional<UserMembership> findByUserId(Long userId);

    /**
     * 批量按 userId 拉会员记录 — 避免列表场景下逐条 select 触发 N+1。
     * <p>使用方应自行转 Map：{@code stream().collect(toMap(UserMembership::getUserId, m -> m))}
     */
    List<UserMembership> findByUserIdIn(Collection<Long> userIds);
}
