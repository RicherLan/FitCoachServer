package com.lanprojects.fitcoach.admin.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 审计日志异步执行器。
 *
 * <p>独立线程池的原因：
 * <ul>
 *   <li>与业务异步任务（会员激活等）隔离，互不抢占；</li>
 *   <li>容量受控（小队列 + 丢弃策略）—— 即便短时审计写入风暴也不会撑爆 JVM 内存；</li>
 *   <li>满了就丢最旧的（DiscardOldestPolicy）—— 审计可丢，业务不可慢。</li>
 * </ul>
 *
 * <p>Bean 名 "auditLogExecutor" 与 {@link AdminAuditLogService#persistAsync} 的
 * {@code @Async("auditLogExecutor")} 对齐。
 */
@Slf4j
@Configuration
public class AdminAuditAsyncConfig {

    @Bean(name = "auditLogExecutor")
    public ThreadPoolTaskExecutor auditLogExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setThreadNamePrefix("admin-audit-");
        // 审计是非常轻量的单条 insert，2 个线程足够支撑日常吞吐
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(1000);
        // 拒绝策略：丢弃队列最旧的任务（审计可丢，不要拖慢业务调用方）
        RejectedExecutionHandler handler = new ThreadPoolExecutor.DiscardOldestPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                log.warn("[audit] executor queue full, oldest task dropped (queue cap={})", e.getQueue().size());
                super.rejectedExecution(r, e);
            }
        };
        exec.setRejectedExecutionHandler(handler);
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(5);
        exec.initialize();
        return exec;
    }
}
