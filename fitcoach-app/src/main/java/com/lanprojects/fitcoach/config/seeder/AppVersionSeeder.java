package com.lanprojects.fitcoach.config.seeder;

import com.lanprojects.fitcoach.appversion.entity.AppVersionEntity;
import com.lanprojects.fitcoach.appversion.repository.AppVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 初始 App 版本播种 —— 让 AdminManager「版本管理」页面在全新数据库下也有可见样本，
 * 同时确保 App 端「检查更新」接口能拉到合理 fallback。
 *
 * <p><b>初始数据策略</b>：
 * <ul>
 *   <li>版本号取当前 RN 项目 package.json 的 1.0.0（versionCode = 1_000_000）；</li>
 *   <li>{@code isPublished = false}（草稿态）—— App 端永远拿不到该记录，因此装着 1.0.0
 *       客户端不会被自动弹"有新版本"。运营在 admin 后台填好真实下载链接 + 改 isPublished=true 后才生效；</li>
 *   <li>android / ios 各播一条，对应未来真正发布时只需 admin 端点击「编辑 → 发布」</li>
 * </ul>
 *
 * <p><b>幂等</b>：按 (platform, versionCode) 检测，已存在则跳过，不覆盖运营在 admin 端的任何编辑。
 */
@Slf4j
@Order(50)
@Component
@RequiredArgsConstructor
public class AppVersionSeeder implements CommandLineRunner {

    private final AppVersionRepository appVersionRepository;

    @Override
    public void run(String... args) {
        int inserted = 0;
        inserted += ensure(
                "android",
                "1.0.0",
                1_000_000,
                "首次发布版本。\n· 完成核心训练流程\n· 支持微信 / 手机号登录",
                // 占位 Play Store 链接，admin 在后台改为真实 packageName
                "https://play.google.com/store/apps/details?id=com.fitcoach.app",
                false);
        inserted += ensure(
                "ios",
                "1.0.0",
                1_000_000,
                "首次发布版本。\n· 完成核心训练流程\n· 支持 Apple 登录 + IAP 订阅",
                // 占位 App Store 链接，admin 在后台改为真实 appId
                "https://apps.apple.com/app/id000000000",
                false);

        if (inserted > 0) {
            log.info("[seeder] App 版本初始化完成，新增 {} 项（草稿态，admin 后台发布后 App 端可见）", inserted);
        }
    }

    private int ensure(String platform, String versionName, int versionCode,
                       String releaseNotes, String downloadUrl, boolean published) {
        if (appVersionRepository.existsByPlatformAndVersionCode(platform, versionCode)) {
            return 0;
        }
        AppVersionEntity v = new AppVersionEntity();
        v.setPlatform(platform);
        v.setVersionName(versionName);
        v.setVersionCode(versionCode);
        v.setReleaseNotes(releaseNotes);
        v.setDownloadUrl(downloadUrl);
        v.setIsForce(false);
        v.setIsPublished(published);
        appVersionRepository.save(v);
        log.info("[seeder] 创建版本：{} {} (vc={}) published={}",
                platform, versionName, versionCode, published);
        return 1;
    }
}
