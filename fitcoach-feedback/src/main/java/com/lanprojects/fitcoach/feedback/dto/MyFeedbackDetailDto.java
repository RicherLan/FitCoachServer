package com.lanprojects.fitcoach.feedback.dto;

import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import com.lanprojects.fitcoach.feedback.entity.FeedbackType;
import com.lanprojects.fitcoach.feedback.entity.UserFeedback;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户侧反馈详情 DTO。
 * <p>
 * 返回完整内容、附件 URL 列表、处理状态和管理员回复，不暴露内部字段（uid / handlerAdmin）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyFeedbackDetailDto {

    private Long id;
    private FeedbackType type;
    private String content;
    private List<String> attachmentUrls;
    private FeedbackStatus status;
    /** 管理员处理回复（未回复时为 null） */
    private String handlerReply;
    /** 处理时间（未处理时为 null） */
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;

    public static MyFeedbackDetailDto from(UserFeedback entity) {
        return MyFeedbackDetailDto.builder()
                .id(entity.getId())
                .type(entity.getType())
                .content(entity.getContent())
                .attachmentUrls(entity.getAttachmentUrls())
                .status(entity.getStatus())
                .handlerReply(entity.getHandlerReply())
                .handledAt(entity.getHandledAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
