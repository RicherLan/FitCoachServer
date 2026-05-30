package com.lanprojects.fitcoach;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FitCoach 后端服务启动入口
 *
 * <p>{@code @EnableScheduling} 用于驱动 fitcoach-log 模块的 LogTaskScheduler
 * （UPLOADING 超时回滚 / PENDING 过期 / UPLOADED 7 天清盘）三类周期任务。
 *
 * <p>{@code @EnableAsync} 用于驱动：
 * <ul>
 *   <li>{@code MembershipService.onPaymentSucceeded}：支付成功后异步激活会员，不阻塞回调主线程；</li>
 *   <li>{@code AdminAuditLogService.persistAsync}：后台高危操作审计日志异步落库，不拖慢业务接口。</li>
 * </ul>
 * 自定义线程池由对应模块的 {@code @Bean TaskExecutor} 提供（按 bean 名匹配）。
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class FitCoachApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitCoachApplication.class, args);
    }
}
