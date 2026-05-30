package com.lanprojects.fitcoach.common.cache;

/**
 * 项目内统一的 {@link org.springframework.cache.annotation.Cacheable} 命名空间常量。
 *
 * <p>统一在此声明的好处：
 * <ul>
 *   <li>{@link CacheConfig} 注册 Caffeine 实例时按名字配置不同 TTL / 容量；</li>
 *   <li>业务侧 @Cacheable / @CacheEvict 使用常量引用，避免拼写错误；</li>
 *   <li>新增缓存时只在这里追加即可，对应 {@link CacheConfig} 自动配置默认策略，
 *       特殊 TTL 需求在 {@link CacheConfig#cacheManager} 显式覆盖。</li>
 * </ul>
 */
public final class CacheNames {

    private CacheNames() {}

    /** 客户端启用的会员套餐列表（高频读 / 低频写，admin 改套餐后 evict） */
    public static final String MEMBERSHIP_PLANS_ENABLED = "membership.plans.enabled";

    /** Admin Dashboard 概览（每次访问 10+ count，短 TTL 缓冲并发刷新） */
    public static final String ADMIN_DASHBOARD_OVERVIEW = "admin.dashboard.overview";
}
