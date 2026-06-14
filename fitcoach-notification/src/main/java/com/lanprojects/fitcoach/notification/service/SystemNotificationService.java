package com.lanprojects.fitcoach.notification.service;

import com.lanprojects.fitcoach.common.client.ClientContext;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.notification.entity.NotificationStatus;
import com.lanprojects.fitcoach.notification.entity.SystemNotificationEntity;
import com.lanprojects.fitcoach.notification.entity.TargetAudience;
import com.lanprojects.fitcoach.notification.repository.SystemNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统通知核心服务（客户端 poll + admin CRUD 共用）。
 *
 * <p><b>职责切分</b>：
 * <ul>
 *   <li>{@link #findAllForUser(String)} —— 客户端 poll 入口，返回当前 uid 可见的所有有效通知 List；</li>
 *   <li>{@link #listAll()} / {@link #listByStatus(NotificationStatus)} —— Admin 列表；</li>
 *   <li>{@link #create} / {@link #update} / {@link #delete} —— Admin 写操作（含校验）。</li>
 * </ul>
 *
 * <p><b>"过期天数 → expire_at" 换算</b>：admin 创建时传入 {@code expireDays}（1-365），
 * 在 {@link #buildExpireAt(LocalDateTime, Integer)} 里换算成 {@code effectiveAt + N 天}。
 * 历史已落库的 expireAt 不会因 admin 后续修改 expireDays 而自动重算 —— admin 想改就显式 patch。
 *
 * <p><b>"一次返回 List" 设计</b>（v1.1 改动）：原来每次 poll 只回最新一条，客户端要等下一次轮询才能看到下一条；
 * 现在服务端一次性返回所有当前可见通知（按 created_at DESC，上限 {@link #MAX_POLL_RETURN}），客户端
 * 队列依次弹出（关一个再弹下一个），用户体验更顺畅；
 * 同时本方法把"投放对象 + 平台 + 版本"全部在服务端过滤完，客户端拿到的就是干净可弹的列表，
 * 不再需要客户端做版本/平台判定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemNotificationService {

    /** 过期天数下限（含），admin 至少能弹 1 天 */
    public static final int MIN_EXPIRE_DAYS = 1;
    /** 过期天数上限（含），运营常用"30 天"作为最长，不允许设置成"永久" */
    public static final int MAX_EXPIRE_DAYS = 365;

    /** target_uids 列表大小上限：本期场景一条通知最多发给数十个用户；超过应拆分或改 ALL */
    public static final int MAX_TARGET_UIDS = 200;

    /**
     * 单次 poll 返回的通知数量上限。
     * <p>本期实际并发量极低（运营 + 召回类合计每月数条），20 是"远超合理上限"的安全阈值；
     * 命中此上限通常意味着运营误操作（如忘了把过期通知改 ARCHIVED），日志会 warn。
     */
    public static final int MAX_POLL_RETURN = 20;

    /** 合法平台值（小写），与 {@code ClientVersionInfo.platform()} 取值一致 */
    private static final Set<String> VALID_PLATFORMS = Set.of("android", "ios");

    private final SystemNotificationRepository repository;

    // ====== 客户端 poll 入口 ======

    /**
     * 客户端 poll 核心查询：取对该 uid 当前所有可见的、有效的通知 List（按 created_at DESC）。
     *
     * <p>返回空 List 表示"暂无通知" —— 客户端不弹任何窗。
     *
     * <p><b>过滤维度</b>（全部在服务端完成）：
     * <ol>
     *   <li>{@code status = PUBLISHED} —— DRAFT/ARCHIVED 客户端永远拉不到；</li>
     *   <li>{@code effectiveAt <= now < expireAt} —— 时间窗口；</li>
     *   <li>{@code targetAudience} —— ALL 全员命中；SPECIFIC_USERS 需 uid 在列表中；</li>
     *   <li>{@code platforms} —— 空/null 命中全平台；否则当前 {@link ClientContext#platform()} 必须在 CSV 中；</li>
     *   <li>{@code minVersionCode / maxVersionCode} —— 当前 {@link ClientContext#nativeVersionCode()} 必须落在区间内（任一为 null 视为该侧不限）。</li>
     * </ol>
     *
     * <p>实现细节：DB 用 LIKE 做 target_uids/platforms 的快速候选筛选；service 用 split+contains 做精确匹配，
     * 防 {@code uid_a} LIKE 误命中 {@code uid_aa} 这类前缀串问题。版本号在 DB 层就精确比较，无需二次过滤。
     */
    public List<SystemNotificationEntity> findAllForUser(String uid) {
        if (uid == null || uid.isBlank()) {
            return Collections.emptyList();
        }
        LocalDateTime now = LocalDateTime.now();

        // 从 ThreadLocal 取当前请求的平台/版本号；admin 后台等场景为 null/0，
        // 这种"未上报"请求会被版本筛选自然拦截（详见 Repository JPQL 注释）。
        String platform = ClientContext.platform();
        int versionCode = ClientContext.nativeVersionCode();

        // DB 候选拉一批；超过 MAX_POLL_RETURN 的二次过滤后丢弃，并 warn 日志提示运营。
        // 拉 MAX_POLL_RETURN * 2 是给 LIKE 误命中留余量（target_uids 前缀串导致的假阳性会被 service 二次过滤剔除）。
        List<SystemNotificationEntity> candidates = repository.findActiveCandidatesForUser(
                NotificationStatus.PUBLISHED, now,
                TargetAudience.ALL, TargetAudience.SPECIFIC_USERS,
                uid, platform, versionCode,
                PageRequest.of(0, MAX_POLL_RETURN * 2));

        List<SystemNotificationEntity> result = new ArrayList<>(MAX_POLL_RETURN);
        for (SystemNotificationEntity n : candidates) {
            if (!matchesUser(n, uid)) continue;
            if (!matchesPlatform(n, platform)) continue;
            // 版本号在 SQL 层已精确过滤；二次校验仅做防御性兜底（防 DB 层 NULL 比较语义不一致等极端情况）。
            if (!matchesVersion(n, versionCode)) continue;
            result.add(n);
            if (result.size() >= MAX_POLL_RETURN) {
                log.warn("[sysnotif] poll uid={} 命中通知数已达上限 {}，可能存在过多未归档的有效通知，请运营核查",
                        uid, MAX_POLL_RETURN);
                break;
            }
        }
        return result;
    }

    /** 精确判定一条通知是否命中目标 uid（防 LIKE 前缀误命中） */
    private boolean matchesUser(SystemNotificationEntity n, String uid) {
        if (n.getTargetAudience() == TargetAudience.ALL) {
            return true;
        }
        if (n.getTargetAudience() == TargetAudience.SPECIFIC_USERS) {
            return parseCsv(n.getTargetUids()).contains(uid);
        }
        return false;
    }

    /**
     * 精确判定一条通知是否命中当前请求平台。
     * <p>{@code platforms} 为空 = 不限平台（永远 true）；
     * 当前请求 {@code platform} 为 null（admin/Postman 未上报） + 通知设置了限制 → 不命中，
     * 这是期望行为：限平台的通知不应推给"无法判定平台"的客户端。
     */
    private boolean matchesPlatform(SystemNotificationEntity n, String platform) {
        String csv = n.getPlatforms();
        if (csv == null || csv.isBlank()) return true;
        if (platform == null || platform.isBlank()) return false;
        return parseCsv(csv).contains(platform);
    }

    /**
     * 精确判定一条通知是否命中当前请求版本号。
     * <p>min/max 同为 null = 不限版本（永远 true）。
     * <p>SQL 层已做相同判定，本方法是防御性兜底，正常情况下与 SQL 结果一致。
     */
    private boolean matchesVersion(SystemNotificationEntity n, int versionCode) {
        Integer min = n.getMinVersionCode();
        Integer max = n.getMaxVersionCode();
        if (min != null && versionCode < min) return false;
        if (max != null && versionCode > max) return false;
        return true;
    }

    /** 把 "a,b, c" 解析成 Set（去重 + trim + 过滤空串），target_uids 和 platforms 共用 */
    private Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    // ====== Admin 查询 ======

    public List<SystemNotificationEntity> listAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<SystemNotificationEntity> listByStatus(NotificationStatus status) {
        return repository.findByStatusOrderByCreatedAtDesc(status);
    }

    public SystemNotificationEntity findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.SYS_NOTIFICATION_NOT_FOUND));
    }

    // ====== Admin 写操作 ======

    /**
     * 创建一条系统通知。
     *
     * @param entity      已填充展示字段 / 投放对象 / 状态 等的 entity（id 必为 null）
     * @param expireDays  过期天数（1-365），将换算成 expireAt = effectiveAt + N 天
     * @param createdBy   操作 admin 用户名（写入审计字段，可空）
     */
    public SystemNotificationEntity create(SystemNotificationEntity entity, Integer expireDays, String createdBy) {
        entity.setId(null);
        validateBasicFields(entity);
        validateExpireDays(expireDays);

        // effectiveAt 缺省 = now（立即生效）；admin 想做定时上架时自己传未来时间
        LocalDateTime now = LocalDateTime.now();
        if (entity.getEffectiveAt() == null) {
            entity.setEffectiveAt(now);
        }
        entity.setExpireAt(buildExpireAt(entity.getEffectiveAt(), expireDays));

        if (entity.getStatus() == null) {
            entity.setStatus(NotificationStatus.DRAFT);
        }
        normalizeTargetUids(entity);
        normalizePlatforms(entity);
        entity.setCreatedBy(createdBy);

        SystemNotificationEntity saved = repository.save(entity);
        log.info("[sysnotif] 创建通知 id={} status={} audience={} expireAt={} createdBy={}",
                saved.getId(), saved.getStatus(), saved.getTargetAudience(), saved.getExpireAt(), createdBy);
        return saved;
    }

    /**
     * 更新一条通知（PATCH 语义：null = 不动）。
     *
     * <p>{@code expireDays} 不为 null 时按"以 existing.effectiveAt 为基准 + N 天"重算 expireAt。
     * 如果 admin 改了 effectiveAt 且同时传了 expireDays，则按 patch.effectiveAt + N 天算。
     *
     * <p>状态切换没有特殊保护 —— admin 可以把 PUBLISHED 改回 DRAFT（紧急下架）。
     */
    public SystemNotificationEntity update(Long id, SystemNotificationEntity patch, Integer expireDays) {
        SystemNotificationEntity existing = findById(id);

        if (patch.getTitle() != null) existing.setTitle(patch.getTitle());
        if (patch.getMessage() != null) existing.setMessage(patch.getMessage());
        if (patch.getPrimaryButtonText() != null) existing.setPrimaryButtonText(patch.getPrimaryButtonText());
        if (patch.getPrimaryButtonUrl() != null) existing.setPrimaryButtonUrl(patch.getPrimaryButtonUrl());
        // 副按钮支持显式置空（admin 想把双按钮改成单按钮）：用空字符串表示"清除"
        if (patch.getSecondaryButtonText() != null) {
            existing.setSecondaryButtonText(patch.getSecondaryButtonText().isBlank() ? null : patch.getSecondaryButtonText());
        }
        if (patch.getSecondaryButtonUrl() != null) {
            existing.setSecondaryButtonUrl(patch.getSecondaryButtonUrl().isBlank() ? null : patch.getSecondaryButtonUrl());
        }
        if (patch.getTargetAudience() != null) existing.setTargetAudience(patch.getTargetAudience());
        // targetUids 同样支持显式置空（切换成 ALL 时）
        if (patch.getTargetUids() != null) {
            existing.setTargetUids(patch.getTargetUids().isBlank() ? null : patch.getTargetUids());
        }
        if (patch.getEffectiveAt() != null) existing.setEffectiveAt(patch.getEffectiveAt());
        if (patch.getStatus() != null) existing.setStatus(patch.getStatus());

        // 平台 / 版本范围：null = 不动；空字符串 / 等同 unset 由 normalize 兜底
        // 注意：本期 PATCH 不支持"显式把 minVersionCode 清空"，admin 想清空需走"重建"或显式传 -1（暂未支持）；
        // 实际运营场景没碰到过"需要从带版本限制改成完全不限"，需要时再扩展即可。
        if (patch.getPlatforms() != null) {
            existing.setPlatforms(patch.getPlatforms().isBlank() ? null : patch.getPlatforms());
        }
        if (patch.getMinVersionCode() != null) existing.setMinVersionCode(patch.getMinVersionCode());
        if (patch.getMaxVersionCode() != null) existing.setMaxVersionCode(patch.getMaxVersionCode());

        if (expireDays != null) {
            validateExpireDays(expireDays);
            existing.setExpireAt(buildExpireAt(existing.getEffectiveAt(), expireDays));
        }

        validateBasicFields(existing);
        normalizeTargetUids(existing);
        normalizePlatforms(existing);

        SystemNotificationEntity saved = repository.save(existing);
        log.info("[sysnotif] 更新通知 id={} status={} audience={} platforms={} versionRange=[{},{}] expireAt={}",
                saved.getId(), saved.getStatus(), saved.getTargetAudience(),
                saved.getPlatforms(), saved.getMinVersionCode(), saved.getMaxVersionCode(), saved.getExpireAt());
        return saved;
    }

    /** 硬删除一条通知 —— 客户端的 seenIds 仍可能残留这个 id，但永远拉不到内容，无副作用 */
    public void delete(Long id) {
        SystemNotificationEntity existing = findById(id);
        repository.delete(existing);
        log.info("[sysnotif] 删除通知 id={}", id);
    }

    // ====== 校验 ======

    private void validateBasicFields(SystemNotificationEntity e) {
        if (e.getTitle() == null || e.getTitle().isBlank()) {
            throw new BusinessException(ResultCode.SYS_NOTIFICATION_TITLE_INVALID);
        }
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            throw new BusinessException(ResultCode.SYS_NOTIFICATION_MESSAGE_INVALID);
        }
        if (e.getPrimaryButtonText() == null || e.getPrimaryButtonText().isBlank()) {
            throw new BusinessException(ResultCode.SYS_NOTIFICATION_PRIMARY_BUTTON_INVALID);
        }
        if (e.getPrimaryButtonUrl() == null) {
            // 允许空字符串（仅关闭弹窗），不允许 null（防 NPE）
            e.setPrimaryButtonUrl("");
        }
        if (e.getTargetAudience() == TargetAudience.SPECIFIC_USERS) {
            Set<String> uids = parseCsv(e.getTargetUids());
            if (uids.isEmpty()) {
                throw new BusinessException(ResultCode.SYS_NOTIFICATION_TARGET_UIDS_EMPTY);
            }
            if (uids.size() > MAX_TARGET_UIDS) {
                throw new BusinessException(ResultCode.SYS_NOTIFICATION_TARGET_UIDS_TOO_MANY);
            }
        }
        validatePlatforms(e.getPlatforms());
        validateVersionRange(e.getMinVersionCode(), e.getMaxVersionCode());
    }

    /**
     * 校验 platforms 字段：null/空 = 不限平台（合法）；
     * 否则每个元素（大小写不敏感）必须是 {@link #VALID_PLATFORMS} 中的值，禁止 "windows" 等未来值。
     * <p>注意：本方法在 {@link #normalizePlatforms(SystemNotificationEntity)} 之前调用，所以需要自己做 lowerCase。
     */
    private void validatePlatforms(String csv) {
        if (csv == null || csv.isBlank()) return;
        Set<String> set = parseCsv(csv);
        if (set.isEmpty()) return; // 全是空白 = 视同 null
        for (String p : set) {
            if (!VALID_PLATFORMS.contains(p.toLowerCase())) {
                throw new BusinessException(ResultCode.SYS_NOTIFICATION_PLATFORMS_INVALID);
            }
        }
    }

    /**
     * 校验版本号区间：min/max 都允许 null；若都非 null，必须 min &lt;= max；
     * 不允许负数（versionCode 物理意义就是非负 int）。
     */
    private void validateVersionRange(Integer min, Integer max) {
        if (min != null && min < 0) {
            throw new BusinessException(ResultCode.SYS_NOTIFICATION_VERSION_RANGE_INVALID);
        }
        if (max != null && max < 0) {
            throw new BusinessException(ResultCode.SYS_NOTIFICATION_VERSION_RANGE_INVALID);
        }
        if (min != null && max != null && min > max) {
            throw new BusinessException(ResultCode.SYS_NOTIFICATION_VERSION_RANGE_INVALID);
        }
    }

    private void validateExpireDays(Integer expireDays) {
        if (expireDays == null || expireDays < MIN_EXPIRE_DAYS || expireDays > MAX_EXPIRE_DAYS) {
            throw new BusinessException(ResultCode.SYS_NOTIFICATION_EXPIRE_DAYS_INVALID);
        }
    }

    /** 把"模糊的 csv"标准化成"去重 + trim + 逗号拼接"的形式（便于精确 LIKE 命中 + 展示一致） */
    private void normalizeTargetUids(SystemNotificationEntity e) {
        if (e.getTargetAudience() != TargetAudience.SPECIFIC_USERS) {
            e.setTargetUids(null);
            return;
        }
        Set<String> uids = parseCsv(e.getTargetUids());
        e.setTargetUids(String.join(",", uids));
    }

    /**
     * 把 platforms 字段标准化：trim + 小写 + 去重 + 逗号拼接；
     * 全空 / 仅空白 → 落 null（DB 层用 IS NULL 走"不限平台"快路径）。
     * <p>因为 service 已先调 {@link #validatePlatforms(String)} 校验过元素合法，
     * 这里只做格式归一，不再二次校验。
     */
    private void normalizePlatforms(SystemNotificationEntity e) {
        String csv = e.getPlatforms();
        if (csv == null || csv.isBlank()) {
            e.setPlatforms(null);
            return;
        }
        // 小写归一：admin 录入大小写不敏感，但落库统一小写，方便和 ClientContext.platform() 直接 contains
        Set<String> set = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(HashSet::new));
        if (set.isEmpty()) {
            e.setPlatforms(null);
        } else {
            e.setPlatforms(String.join(",", set));
        }
    }

    private LocalDateTime buildExpireAt(LocalDateTime effectiveAt, Integer expireDays) {
        return effectiveAt.plusDays(expireDays);
    }
}
