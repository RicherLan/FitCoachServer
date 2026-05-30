package com.lanprojects.fitcoach.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局 Caffeine 缓存配置 — 项目内所有 {@code @Cacheable} / {@code @CacheEvict}
 * 都通过这里注册的 {@link CacheManager} 路由。
 *
 * <p>设计要点：
 * <ul>
 *   <li>每个命名 cache 独立 spec（不同 TTL/容量/统计开关）；</li>
 *   <li>SimpleCacheManager + 显式声明 cache 列表，避免运行时 dynamic 缓存名打错也不报错；</li>
 *   <li>未来要切到 Redis 只需替换此 Bean，业务层 {@code @Cacheable} 一行不动。</li>
 * </ul>
 *
 * <p>本配置启用条件：默认开启；如要全局关闭走 {@code spring.cache.type=none}
 * （Spring Boot 标准开关，会跳过 @EnableCaching 装配的所有 CacheManager）。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 套餐缓存：TTL 5 分钟，admin 改套餐后会主动 evictAll，
     * 客户端两次刷新最差延迟 5 分钟生效（业务可接受）。
     */
    private static final String SPEC_MEMBERSHIP_PLANS =
            "maximumSize=64,expireAfterWrite=5m,recordStats";

    /**
     * Dashboard 概览缓存：TTL 30 秒。
     * <p>多个 admin 同时打开首页时只查一次库；30 秒内的数据滞后业务可接受
     *   （概览是趋势数据，不需要秒级精度）。
     */
    private static final String SPEC_ADMIN_DASHBOARD =
            "maximumSize=8,expireAfterWrite=30s,recordStats";

    @Bean
    public CacheManager cacheManager() {
        List<CaffeineCache> caches = new ArrayList<>();
        caches.add(buildCache(CacheNames.MEMBERSHIP_PLANS_ENABLED, SPEC_MEMBERSHIP_PLANS));
        caches.add(buildCache(CacheNames.ADMIN_DASHBOARD_OVERVIEW, SPEC_ADMIN_DASHBOARD));

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(caches);
        return manager;
    }

    private CaffeineCache buildCache(String name, String spec) {
        Caffeine<Object, Object> builder = Caffeine.from(CaffeineSpec.parse(spec));
        return new CaffeineCache(name, builder.build());
    }
}
