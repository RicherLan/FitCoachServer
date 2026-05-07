package com.lanprojects.fitcoach;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FitCoach 后端服务启动入口
 *
 * <p>{@code @EnableScheduling} 用于驱动 fitcoach-log 模块的 LogTaskScheduler
 * （UPLOADING 超时回滚 / PENDING 过期 / UPLOADED 7 天清盘）三类周期任务。
 */
@SpringBootApplication
@EnableScheduling
public class FitCoachApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitCoachApplication.class, args);
    }
}
