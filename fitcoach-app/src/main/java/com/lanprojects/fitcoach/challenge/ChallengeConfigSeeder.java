package com.lanprojects.fitcoach.challenge;

import com.lanprojects.fitcoach.common.config.entity.SysConfig;
import com.lanprojects.fitcoach.common.config.repository.SysConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChallengeConfigSeeder implements CommandLineRunner {
    private final SysConfigRepository configs;
    @Override public void run(String... args) {
        ensure("challenge.enabled", "运动挑战总开关；关闭仅禁止新开局，保留历史和已开始场次结算");
        ensure("challenge.pushup.enabled", "俯卧撑挑战开关；同时开启总开关才可开始，客户端需支持 pushup-60-v1");
    }
    private void ensure(String key, String description) {
        if (configs.findByConfigKey(key).isEmpty()) {
            // 默认开放挑战；仅补齐缺失项，保留管理员已保存的关闭设置。
            configs.save(new SysConfig(key, "true", "challenge", description));
        }
    }
}
