package com.lanprojects.fitcoach.feedback.dto;

import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import com.lanprojects.fitcoach.feedback.entity.FeedbackType;
import com.lanprojects.fitcoach.feedback.entity.UserFeedback;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户侧"我的反馈"列表项 DTO。
 * <p>
 * 与 admin 侧 {@code FeedbackSummaryDto} 不同：
 * <ul>
 *   <li>不暴露 uid / handlerAdmin — 用户只需看自己的反馈；</li>
 *   <li>内容截取前 100 字做预览，附件只返回数量；</li>
 *   <li>status + handlerReply 让用户知道反馈是否被处理、回复了什么。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyFeedbackSummaryDto {

    private Long id;
    private FeedbackType type;
    /** 内容预览（≤100 字） */
    private String contentPreview;
    /** 附件数量 */
    private int attachmentCount;
    private FeedbackStatus status;
    /** 管理员回复（列表里也带上，便于用户快速扫） */
    private String handlerReply;
    private LocalDateTime createdAt;

    private static final int PREVIEW_MAX_LEN = 100;

    public static MyFeedbackSummaryDto from(UserFeedback entity) {
        String content = entity.getContent();
        String preview = content.length() <= PREVIEW_MAX_LEN
                ? content
                : content.substring(0, PREVIEW_MAX_LEN) + "…";
        return MyFeedbackSummaryDto.builder()
                .id(entity.getId())
                .type(entity.getType())
                .contentPreview(preview)
                .attachmentCount(entity.getAttachmentUrls() == null ? 0 : entity.getAttachmentUrls().size())
                .status(entity.getStatus())
                .handlerReply(entity.getHandlerReply())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
