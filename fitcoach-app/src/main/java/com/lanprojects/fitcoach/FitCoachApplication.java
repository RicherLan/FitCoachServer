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
 *
 * <p>{@code proxyTargetClass = true}：强制 CGLIB 子类代理。
 * 原因：{@code AdminAuditLogService implements AdminAuditPort}，但项目里 13 个 controller
 * 都按"具体类"注入（且使用了接口外的枚举重载方法 logSuccess(AdminAuditAction, ...) 等），
 * 默认 JDK 代理只实现接口、不是具体类 → 启动期 bean 注入失败。
 * 切换到 CGLIB 后代理对象仍是 AdminAuditLogService 子类，兼容老的注入写法。
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync(proxyTargetClass = true)
public class FitCoachApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitCoachApplication.class, args);
    }
}
