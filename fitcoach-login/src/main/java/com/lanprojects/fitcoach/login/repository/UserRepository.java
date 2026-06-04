package com.lanprojects.fitcoach.login.repository;

import com.lanprojects.fitcoach.login.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository
        extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUid(String uid);

    /** 批量按 uid 查询（admin 后台反馈列表回填昵称用，避免 N+1） */
    List<User> findByUidIn(Collection<String> uids);

    Optional<User> findByOpenId(String openId);

    Optional<User> findByUnionId(String unionId);

    Optional<User> findByPhone(String phone);

    Optional<User> findByAccount(String account);

    boolean existsByOpenId(String openId);

    boolean existsByAccount(String account);

    /** 后台 Dashboard：按时间区间统计新增用户数（含 from，不含 to） */
    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    /** 后台 Dashboard：按启用状态统计 */
    long countByEnabled(Boolean enabled);
}
