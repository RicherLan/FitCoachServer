package com.lanprojects.fitcoach.admin.dto;

import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import lombok.Data;

/**
 * 更新反馈状态 / 处理回复的请求体。
 * <p>status 必填；handlerReply 可选（管理员可一边改状态一边回复）。
 */
@Data
public class UpdateFeedbackStatusRequest {
    private FeedbackStatus status;
    /** 管理员回复，可选（最长 500 字，service 校验） */
    private String handlerReply;
}
