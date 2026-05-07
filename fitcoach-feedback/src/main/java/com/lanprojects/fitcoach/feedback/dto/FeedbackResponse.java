package com.lanprojects.fitcoach.feedback.dto;

import com.lanprojects.fitcoach.feedback.entity.FeedbackType;
import com.lanprojects.fitcoach.feedback.entity.UserFeedback;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈响应（创建成功后返回；后续运营端列表也复用）。
 * <p>不直接返回 {@link UserFeedback} 实体，避免泄露内部字段（id / 私有字段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResponse {

    private Long id;
    private FeedbackType type;
    private String content;
    private List<String> attachmentUrls;
    private LocalDateTime createdAt;

    public static FeedbackResponse from(UserFeedback entity) {
        return FeedbackResponse.builder()
                .id(entity.getId())
                .type(entity.getType())
                .content(entity.getContent())
                .attachmentUrls(entity.getAttachmentUrls())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
