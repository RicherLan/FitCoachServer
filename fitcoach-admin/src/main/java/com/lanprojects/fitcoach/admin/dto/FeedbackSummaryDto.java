package com.lanprojects.fitcoach.admin.dto;

import com.lanprojects.fitcoach.feedback.entity.UserFeedback;
import lombok.Builder;
import lombok.Data;

/**
 * 反馈列表项 DTO（admin 后台用）
 * <p>不展开 attachmentUrls，只返回数量；详情接口才返回完整 URL 列表。
 */
@Data
@Builder
public class FeedbackSummaryDto {
    private Long id;
    /** 提交者 uid（详情可点入查看用户） */
    private String uid;
    /** 提交者昵称（关联 User 表，缺失时为空字符串） */
    private String nickname;
    private String type;
    private String status;
    /** 内容预览（最多 80 字，避免列表过长） */
    private String contentPreview;
    private Integer attachmentCount;
    private String platform;
    private String appVersion;
    private String handlerAdmin;
    private Long createdAt;
    private Long handledAt;

    public static FeedbackSummaryDto from(UserFeedback fb, String nickname) {
        String content = fb.getContent() == null ? "" : fb.getContent();
        String preview = content.length() <= 80 ? content : content.substring(0, 80) + "…";
        return FeedbackSummaryDto.builder()
                .id(fb.getId())
                .uid(fb.getUid())
                .nickname(nickname == null ? "" : nickname)
                .type(fb.getType() == null ? null : fb.getType().name())
                .status(fb.getStatus() == null ? "PENDING" : fb.getStatus().name())
                .contentPreview(preview)
                .attachmentCount(fb.getAttachmentUrls() == null ? 0 : fb.getAttachmentUrls().size())
                .platform(fb.getPlatform())
                .appVersion(fb.getAppVersion())
                .handlerAdmin(fb.getHandlerAdmin())
                .createdAt(toMillis(fb.getCreatedAt()))
                .handledAt(toMillis(fb.getHandledAt()))
                .build();
    }

    private static Long toMillis(java.time.LocalDateTime t) {
        return t == null ? null : t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
