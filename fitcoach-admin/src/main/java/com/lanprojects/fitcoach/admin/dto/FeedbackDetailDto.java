package com.lanprojects.fitcoach.admin.dto;

import com.lanprojects.fitcoach.feedback.entity.UserFeedback;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 反馈详情 DTO（admin 后台用），相比 Summary 多返回完整 content + 附件 URL 列表 + 处理回复
 */
@Data
@Builder
public class FeedbackDetailDto {
    private Long id;
    private String uid;
    private String nickname;
    /** 提交者头像（绝对 URL） */
    private String avatarUrl;
    private String type;
    private String status;
    private String content;
    /** 完整附件 URL 列表（已转为绝对 URL） */
    private List<String> attachmentUrls;
    private String platform;
    private String appVersion;
    private String handlerAdmin;
    private String handlerReply;
    private Long createdAt;
    private Long handledAt;

    public static FeedbackDetailDto from(UserFeedback fb,
                                         String nickname,
                                         String avatarUrlAbsolute,
                                         List<String> attachmentUrlsAbsolute) {
        return FeedbackDetailDto.builder()
                .id(fb.getId())
                .uid(fb.getUid())
                .nickname(nickname == null ? "" : nickname)
                .avatarUrl(avatarUrlAbsolute)
                .type(fb.getType() == null ? null : fb.getType().name())
                .status(fb.getStatus() == null ? "PENDING" : fb.getStatus().name())
                .content(fb.getContent())
                .attachmentUrls(attachmentUrlsAbsolute)
                .platform(fb.getPlatform())
                .appVersion(fb.getAppVersion())
                .handlerAdmin(fb.getHandlerAdmin())
                .handlerReply(fb.getHandlerReply())
                .createdAt(toMillis(fb.getCreatedAt()))
                .handledAt(toMillis(fb.getHandledAt()))
                .build();
    }

    private static Long toMillis(java.time.LocalDateTime t) {
        return t == null ? null : t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
