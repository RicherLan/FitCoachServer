package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.notification.entity.NotificationStatus;
import com.lanprojects.fitcoach.notification.entity.SystemNotificationEntity;
import com.lanprojects.fitcoach.notification.entity.TargetAudience;
import com.lanprojects.fitcoach.notification.repository.SystemNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 系统通知初始播种 —— 让 AdminManager「系统通知」页面在全新数据库下有一条样本，
 * 方便运营熟悉字段与流程。
 *
 * <p><b>初始数据策略</b>：
 * <ul>
 *   <li>插入一条 <b>DRAFT</b> 状态的通知 —— 客户端永远拉不到（safety by default），
 *       admin 上线时需要手动切换到 PUBLISHED；</li>
 *   <li>有效期 7 天（从首次播种时刻起算），admin 可在后台编辑；</li>
 *   <li>主按钮挂一个 https 链接示例（点击后客户端用 deeplinkRouter → WebView 打开），
 *       副按钮用空字符串 URL 示意"仅关闭弹窗"。</li>
 * </ul>
 *
 * <p><b>幂等</b>：检测库中是否已存在任何 system_notification 记录，有就跳过 —— 这是"样板播种"
 * 而非"必备配置"，第一次跑过后 admin 在后台的任何增删改都不应被覆盖。
 */
@Slf4j
@Order(60)
@Component
@RequiredArgsConstructor
public class SystemNotificationSeeder implements CommandLineRunner {

    private final SystemNotificationRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        SystemNotificationEntity sample = new SystemNotificationEntity();
        sample.setTitle("欢迎来到 FitCoach");
        sample.setMessage("这是一条示例系统通知。\n"
                + "运营可在 AdminManager「系统通知」页面创建/编辑/下架通知，\n"
                + "客户端将通过轮询接口在合适时机弹出。");
        sample.setPrimaryButtonText("立即查看");
        sample.setPrimaryButtonUrl("migofit://web?url=https%3A%2F%2Fexample.com%2Fwelcome");
        sample.setSecondaryButtonText("我知道了");
        sample.setSecondaryButtonUrl(""); // 仅关闭弹窗
        sample.setTargetAudience(TargetAudience.ALL);
        sample.setEffectiveAt(now);
        sample.setExpireAt(now.plusDays(7));
        sample.setStatus(NotificationStatus.DRAFT);
        sample.setCreatedBy(null); // seeder 创建，无 admin 用户名

        repository.save(sample);
        log.info("[seeder] 系统通知示例已插入（DRAFT 状态，admin 后台发布后客户端可见）");
    }
}
