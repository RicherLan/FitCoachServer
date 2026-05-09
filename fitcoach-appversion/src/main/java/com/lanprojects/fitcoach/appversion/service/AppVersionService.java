package com.lanprojects.fitcoach.appversion.service;

import com.lanprojects.fitcoach.appversion.entity.AppVersionEntity;
import com.lanprojects.fitcoach.appversion.repository.AppVersionRepository;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * App 版本管理核心服务（App 端检查更新 + Admin CRUD 共用）。
 *
 * <p><b>职责切分</b>：
 * <ul>
 *   <li>{@link #findLatestPublished(String)} —— App 端「检查更新」入口；</li>
 *   <li>{@link #listByPlatform(String)} / {@link #listAll()} —— Admin 列表；</li>
 *   <li>{@link #create} / {@link #update} / {@link #delete} —— Admin 写操作（含校验/幂等）。</li>
 * </ul>
 *
 * <p><b>平台白名单</b>：当前仅支持 android / ios，集中在 {@link #ALLOWED_PLATFORMS} 维护，
 * 增加新平台（如鸿蒙 harmony）时只改这里 + admin 表单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppVersionService {

    /** 当前业务支持的平台白名单。新增需同步 RN ClientInfo.platform() */
    public static final Set<String> ALLOWED_PLATFORMS = Set.of("android", "ios");

    private final AppVersionRepository appVersionRepository;

    // ====== 查询 ======

    /**
     * App 端核心查询：取该平台已发布的最新版本（versionCode 最大的一条）。
     * <p>未发布草稿不会被命中；从未发布过任何版本时返回 {@link Optional#empty()}，
     * 调用方（controller）应当返回「无新版本」语义而不是抛异常。
     *
     * @param platform 客户端平台，建议从 ClientVersionInfo.platform() 取，缺失走 query 兜底
     */
    public Optional<AppVersionEntity> findLatestPublished(String platform) {
        validatePlatform(platform);
        return appVersionRepository.findFirstByPlatformAndIsPublishedTrueOrderByVersionCodeDesc(platform);
    }

    /** Admin 端：查全部版本（按平台、versionCode 倒序），含未发布草稿 */
    public List<AppVersionEntity> listAll() {
        return appVersionRepository.findAllByOrderByPlatformAscVersionCodeDesc();
    }

    /** Admin 端：按平台过滤 */
    public List<AppVersionEntity> listByPlatform(String platform) {
        validatePlatform(platform);
        return appVersionRepository.findByPlatformOrderByVersionCodeDesc(platform);
    }

    public AppVersionEntity findById(Long id) {
        return appVersionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.APP_VERSION_NOT_FOUND));
    }

    // ====== Admin 写操作 ======

    /**
     * 创建新版本记录。
     * <p>校验：
     * <ol>
     *   <li>platform 必须在白名单内；</li>
     *   <li>versionCode &gt; 0；</li>
     *   <li>versionName / downloadUrl 非空；</li>
     *   <li>同 (platform, versionCode) 未重复（数据库 unique 约束兜底，service 提前抛出更友好错误）。</li>
     * </ol>
     * <p>{@code isForce} / {@code isPublished} 缺省取 false（草稿态创建，再单独发布）。
     */
    public AppVersionEntity create(AppVersionEntity toCreate) {
        validatePlatform(toCreate.getPlatform());
        validateVersionCode(toCreate.getVersionCode());
        validateVersionName(toCreate.getVersionName());
        validateDownloadUrl(toCreate.getDownloadUrl());
        if (appVersionRepository.existsByPlatformAndVersionCode(toCreate.getPlatform(), toCreate.getVersionCode())) {
            throw new BusinessException(ResultCode.APP_VERSION_DUPLICATE);
        }
        toCreate.setId(null); // 强制走数据库自增
        if (toCreate.getIsForce() == null) {
            toCreate.setIsForce(false);
        }
        if (toCreate.getIsPublished() == null) {
            toCreate.setIsPublished(false);
        }
        AppVersionEntity saved = appVersionRepository.save(toCreate);
        log.info("[appversion] 创建版本 id={} platform={} versionName={} versionCode={} isForce={} isPublished={}",
                saved.getId(), saved.getPlatform(), saved.getVersionName(), saved.getVersionCode(),
                saved.getIsForce(), saved.getIsPublished());
        return saved;
    }

    /**
     * 更新（PATCH 语义：null = 不动）。
     * <p><b>platform 与 versionCode 一旦创建禁止修改</b>（修改会破坏「最新版本」判定的稳定性，
     * 且违反 (platform, versionCode) 的语义不变性 —— 同一条记录指向「同一个安装包」）。
     * <p>真要换版本号请：删旧、建新。
     */
    public AppVersionEntity update(Long id, AppVersionEntity patch) {
        AppVersionEntity existing = findById(id);

        // platform / versionCode 不允许更新（保护语义不变性 + 唯一约束稳定）
        if (patch.getVersionName() != null) {
            validateVersionName(patch.getVersionName());
            existing.setVersionName(patch.getVersionName());
        }
        if (patch.getReleaseNotes() != null) existing.setReleaseNotes(patch.getReleaseNotes());
        if (patch.getDownloadUrl() != null) {
            validateDownloadUrl(patch.getDownloadUrl());
            existing.setDownloadUrl(patch.getDownloadUrl());
        }
        if (patch.getIsForce() != null) existing.setIsForce(patch.getIsForce());
        if (patch.getIsPublished() != null) existing.setIsPublished(patch.getIsPublished());

        AppVersionEntity saved = appVersionRepository.save(existing);
        log.info("[appversion] 更新版本 id={} platform={} versionCode={} isForce={} isPublished={}",
                saved.getId(), saved.getPlatform(), saved.getVersionCode(),
                saved.getIsForce(), saved.getIsPublished());
        return saved;
    }

    /**
     * 硬删除一条版本记录。
     * <p>不做"是否被任何客户端使用"的保护：版本号即将退役 / 误录草稿想清理 都是合理删除场景。
     * 删除最新已发布版本会让 App 端查不到更新（fallback 为「无新版本」），不会破坏现有 App。
     */
    public void delete(Long id) {
        AppVersionEntity existing = findById(id);
        appVersionRepository.delete(existing);
        log.info("[appversion] 删除版本 id={} platform={} versionCode={}",
                id, existing.getPlatform(), existing.getVersionCode());
    }

    // ====== 校验 ======

    private void validatePlatform(String platform) {
        if (platform == null || !ALLOWED_PLATFORMS.contains(platform)) {
            throw new BusinessException(ResultCode.APP_VERSION_PLATFORM_INVALID);
        }
    }

    private void validateVersionCode(Integer versionCode) {
        if (versionCode == null || versionCode <= 0) {
            throw new BusinessException(ResultCode.APP_VERSION_VERSION_CODE_INVALID);
        }
    }

    private void validateVersionName(String versionName) {
        if (versionName == null || versionName.isBlank()) {
            throw new BusinessException(ResultCode.APP_VERSION_VERSION_NAME_INVALID);
        }
    }

    private void validateDownloadUrl(String downloadUrl) {
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new BusinessException(ResultCode.APP_VERSION_DOWNLOAD_URL_INVALID);
        }
    }
}
