package com.lanprojects.fitcoach.admin.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 审计日志查询 Repository。
 *
 * <p>列表查询基本都按 created_at desc + 多维筛选；这里用单条 JPQL 配合可选参数实现，
 * 比写一堆 findXxxAndYyy 重载更易维护。
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /**
     * 多维筛选（参数全部可空）：用户名 / action 名（字符串）/ 目标类型 / 目标 id / 起止时间。
     * <p>用 LIKE 包裹 username 支持模糊（前缀小写匹配）；其余精确等于。
     * <p>当某个参数为 null 时通过 SQL 中的 NULL 判断跳过。
     *
     * @param username 操作员 username（精确等于；null = 不筛）
     * @param action 操作枚举名字符串（精确等于；null = 不筛）
     * @param targetType 目标类型（精确等于；null = 不筛）
     * @param targetId 目标 id（精确等于；null = 不筛）
     * @param start 起始时间（含；null = 不筛）
     * @param end 结束时间（不含；null = 不筛）
     */
    @Query("""
            SELECT a FROM AdminAuditLog a
             WHERE (:username IS NULL OR a.adminUsername = :username)
               AND (:action IS NULL OR a.action = :action)
               AND (:targetType IS NULL OR a.targetType = :targetType)
               AND (:targetId IS NULL OR a.targetId = :targetId)
               AND (:start IS NULL OR a.createdAt >= :start)
               AND (:end IS NULL OR a.createdAt < :end)
             ORDER BY a.createdAt DESC
            """)
    Page<AdminAuditLog> search(
            @Param("username") String username,
            @Param("action") AdminAuditAction action,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );
}
