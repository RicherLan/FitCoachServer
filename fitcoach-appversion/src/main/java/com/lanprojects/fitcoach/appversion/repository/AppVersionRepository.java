package com.lanprojects.fitcoach.appversion.repository;

import com.lanprojects.fitcoach.appversion.entity.AppVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * App 版本持久层。
 *
 * <p>查询场景仅两类：
 * <ol>
 *   <li>App 端：按平台取「已发布的最新版本」（命中
 *       {@code uk_appversion_platform_code} + {@code idx_appversion_platform_published_code} 索引）；</li>
 *   <li>Admin 端：按平台列出全部记录（含未发布草稿），按 versionCode 倒序展示。</li>
 * </ol>
 */
public interface AppVersionRepository extends JpaRepository<AppVersionEntity, Long> {

    /**
     * App 端核心查询：取该平台已发布的最新版本（versionCode 最大的一条）。
     * <p>未发布的草稿态记录被过滤；当某平台从未发布过任何版本时返回 empty，
     * service 层应当当作「无可用更新」处理（不抛异常）。
     */
    Optional<AppVersionEntity> findFirstByPlatformAndIsPublishedTrueOrderByVersionCodeDesc(String platform);

    /** Admin 列表：按平台过滤 + 按 versionCode 倒序（含草稿） */
    List<AppVersionEntity> findByPlatformOrderByVersionCodeDesc(String platform);

    /** Admin 列表（不过滤平台）：全部记录按平台、versionCode 倒序 */
    List<AppVersionEntity> findAllByOrderByPlatformAscVersionCodeDesc();

    /** 写入前幂等检查：同平台 + 同 versionCode 是否已存在 */
    boolean existsByPlatformAndVersionCode(String platform, Integer versionCode);
}
