package com.lanprojects.fitcoach.notification.repository;

import com.lanprojects.fitcoach.notification.entity.NotificationStatus;
import com.lanprojects.fitcoach.notification.entity.SystemNotificationEntity;
import com.lanprojects.fitcoach.notification.entity.TargetAudience;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统通知持久层。
 *
 * <p>查询场景：
 * <ol>
 *   <li>客户端 poll：取"对该 uid 可见的当前所有有效通知（List）"
 *       —— status=PUBLISHED + 时间窗口 + 投放对象命中 + 平台命中 + 版本号区间命中，
 *       按 created_at DESC，最多取 {@code Pageable} 限定的条数；</li>
 *   <li>Admin 列表：可按 status / targetAudience 过滤，按 created_at DESC 分页；</li>
 *   <li>Admin 详情 / 删除：常规 JpaRepository CRUD。</li>
 * </ol>
 */
public interface SystemNotificationRepository extends JpaRepository<SystemNotificationEntity, Long> {

    /**
     * 客户端 poll 核心查询：取对该 uid 可见的、当前有效的通知候选 List（按 created_at DESC）。
     *
     * <p>命中条件（DB 层）：
     * <ul>
     *   <li>{@code status = PUBLISHED}</li>
     *   <li>{@code effective_at &lt;= now &lt; expire_at}</li>
     *   <li>投放对象命中：{@code target_audience = ALL}
     *       或 {@code target_audience = SPECIFIC_USERS AND target_uids LIKE %uid%}（精确匹配在 service 二次校验）</li>
     *   <li>平台命中：{@code platforms IS NULL/空 OR platforms LIKE %platform%}（精确匹配在 service 二次校验）</li>
     *   <li>版本号区间命中：
     *     <ul>
     *       <li>{@code min_version_code IS NULL OR :versionCode >= min_version_code}</li>
     *       <li>{@code max_version_code IS NULL OR :versionCode <= max_version_code}</li>
     *     </ul>
     *     版本字段直接在 DB 层精确比较，无误命中风险，无需 service 二次过滤。
     *   </li>
     * </ul>
     *
     * <p><b>关于 LIKE 误命中</b>：{@code target_uids LIKE %uid_a%} 可能误命中 {@code "uid_aa"}；
     * {@code platforms LIKE %android%} 不会误命中（合法值只有 android/ios，无前缀串问题）；
     * 但为统一处理路径，service 层都会做一次精确二次过滤。
     *
     * <p><b>versionCode = 0 含义</b>：客户端未上报版本（admin 后台、Postman 调试）时
     * {@link com.lanprojects.fitcoach.common.client.ClientContext#nativeVersionCode()} 返回 0。
     * 此时 SQL 里 {@code 0 >= min_version_code} 和 {@code 0 <= max_version_code} 都可能不成立 ——
     * 这是期望行为：未上报版本的请求应被版本筛选拦截，避免运营误把"仅老版本兜底提示"推给后台调试入口。
     * 如果某条通知就是不想限版本，admin 创建时把 min/max 都留空即可。
     *
     * <p>{@code platform = null} 时同理通过"IS NULL OR LIKE" 表达式自然处理。
     */
    @Query("""
        SELECT n FROM SystemNotificationEntity n
        WHERE n.status = :status
          AND n.effectiveAt <= :now
          AND n.expireAt > :now
          AND (n.targetAudience = :allAudience
               OR (n.targetAudience = :specificAudience AND n.targetUids LIKE CONCAT('%', :uid, '%')))
          AND (n.platforms IS NULL OR n.platforms = '' OR :platform IS NULL
               OR n.platforms LIKE CONCAT('%', :platform, '%'))
          AND (n.minVersionCode IS NULL OR :versionCode >= n.minVersionCode)
          AND (n.maxVersionCode IS NULL OR :versionCode <= n.maxVersionCode)
        ORDER BY n.createdAt DESC
        """)
    List<SystemNotificationEntity> findActiveCandidatesForUser(
            @Param("status") NotificationStatus status,
            @Param("now") LocalDateTime now,
            @Param("allAudience") TargetAudience allAudience,
            @Param("specificAudience") TargetAudience specificAudience,
            @Param("uid") String uid,
            @Param("platform") String platform,
            @Param("versionCode") int versionCode,
            Pageable pageable);

    /** Admin 列表（按 status 过滤） */
    List<SystemNotificationEntity> findByStatusOrderByCreatedAtDesc(NotificationStatus status);

    /** Admin 列表（不过滤） */
    List<SystemNotificationEntity> findAllByOrderByCreatedAtDesc();
}
