package com.lanprojects.fitcoach.admin.dto.notification;

import com.lanprojects.fitcoach.notification.entity.NotificationStatus;
import com.lanprojects.fitcoach.notification.entity.SystemNotificationEntity;
import com.lanprojects.fitcoach.notification.entity.TargetAudience;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin 端系统通知创建/更新入参（PATCH 语义）。
 *
 * <p>语义约定：
 * <ul>
 *   <li>创建时必传：title / message / primaryButtonText / targetAudience / expireDays；</li>
 *   <li>更新时（PATCH）所有字段可空，{@code null} 表示不动；</li>
 *   <li>{@code expireDays} 不为 null 时会按 effectiveAt + N 天重算 expireAt；</li>
 *   <li>副按钮要"清除"：传空字符串而不是 null。</li>
 * </ul>
 */
@Data
public class AdminSystemNotificationRequest {

    /** 弹窗标题（最多 100 字） */
    @NotBlank(groups = OnCreate.class)
    @Size(max = 100)
    private String title;

    /** 弹窗正文（最多 1000 字） */
    @NotBlank(groups = OnCreate.class)
    @Size(max = 1000)
    private String message;

    @NotBlank(groups = OnCreate.class)
    @Size(max = 30)
    private String primaryButtonText;

    /** 主按钮 URL（可空字符串：仅关闭弹窗） */
    @Size(max = 1000)
    private String primaryButtonUrl;

    /**
     * 副按钮文案（可空）。
     * <p>更新时若想把双按钮改成单按钮，传 {@code ""}（空串）而不是 null；
     * service 层会把空串识别成"清除"。
     */
    @Size(max = 30)
    private String secondaryButtonText;

    @Size(max = 1000)
    private String secondaryButtonUrl;

    /** 投放对象类型：ALL / SPECIFIC_USERS */
    @NotNull(groups = OnCreate.class)
    private TargetAudience targetAudience;

    /**
     * 指定 uid 列表（CSV，如 "uid_a,uid_b"）。
     * <p>{@code targetAudience = ALL} 时此字段被忽略（service 会强制置 null）。
     */
    @Size(max = 4000)
    private String targetUids;

    /**
     * 过期天数（1-365）。
     * <p>service 层换算：{@code expireAt = effectiveAt + N 天}。
     * <p>更新时不传 = 不重算 expireAt；传 = 按"existing.effectiveAt（如本次也改了则用新的）+ N 天"重算。
     */
    @NotNull(groups = OnCreate.class)
    @Min(1)
    @Max(365)
    private Integer expireDays;

    /** 生效时间（可空，缺省=立即生效） */
    private LocalDateTime effectiveAt;

    /** 发布状态（创建时不传默认 DRAFT；切线上时改成 PUBLISHED） */
    private NotificationStatus status;

    /**
     * 投放平台（CSV，如 {@code "android,ios"}）；null/空 = 不限平台。
     * <p>合法元素仅 {@code android} / {@code ios}（大小写不敏感，service 会归一化成小写）。
     * 与 {@code ClientContext.platform()} 取值一致以便 contains 判定。
     */
    @Size(max = 64)
    private String platforms;

    /**
     * 最小 versionCode（含）。null = 不限下界。
     * <p>versionCode 编码：{@code MAJOR*1_000_000 + MINOR*1_000 + PATCH}（与 fitcoach-appversion 一致）。
     * 例如 "1.2.3" → 1002003。前端 admin 录入 "1.2.3" 或 1002003 均可，提交前统一转 int。
     */
    @Min(0)
    private Integer minVersionCode;

    /**
     * 最大 versionCode（含）。null = 不限上界。
     * <p>语义同 minVersionCode；service 层会校验 minVersionCode ≤ maxVersionCode。
     */
    @Min(0)
    private Integer maxVersionCode;

    public SystemNotificationEntity toCreateEntity() {
        SystemNotificationEntity e = new SystemNotificationEntity();
        e.setTitle(title);
        e.setMessage(message);
        e.setPrimaryButtonText(primaryButtonText);
        e.setPrimaryButtonUrl(primaryButtonUrl == null ? "" : primaryButtonUrl);
        e.setSecondaryButtonText(emptyToNull(secondaryButtonText));
        e.setSecondaryButtonUrl(emptyToNull(secondaryButtonUrl));
        e.setTargetAudience(targetAudience);
        e.setTargetUids(targetUids);
        e.setEffectiveAt(effectiveAt);
        e.setStatus(status);
        e.setPlatforms(emptyToNull(platforms));
        e.setMinVersionCode(minVersionCode);
        e.setMaxVersionCode(maxVersionCode);
        return e;
    }

    public SystemNotificationEntity toPatchEntity() {
        SystemNotificationEntity e = new SystemNotificationEntity();
        e.setTitle(title);
        e.setMessage(message);
        e.setPrimaryButtonText(primaryButtonText);
        e.setPrimaryButtonUrl(primaryButtonUrl);
        // 副按钮：保留空串语义（service 会把空串识别成"清除"），不要在这里转 null
        e.setSecondaryButtonText(secondaryButtonText);
        e.setSecondaryButtonUrl(secondaryButtonUrl);
        e.setTargetAudience(targetAudience);
        e.setTargetUids(targetUids);
        e.setEffectiveAt(effectiveAt);
        e.setStatus(status);
        // platforms / min / max：null = 不动；service 端会把空串归一化成 null（=清除限制）
        e.setPlatforms(platforms);
        e.setMinVersionCode(minVersionCode);
        e.setMaxVersionCode(maxVersionCode);
        return e;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** 仅创建时校验 */
    public interface OnCreate {}
}
