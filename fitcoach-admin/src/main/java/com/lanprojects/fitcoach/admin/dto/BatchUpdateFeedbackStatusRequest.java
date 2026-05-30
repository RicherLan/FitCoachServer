package com.lanprojects.fitcoach.admin.dto;

import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import lombok.Data;

import java.util.List;

/**
 * 批量更新反馈状态的请求体。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>ids 必填且非空；上限由 server 校验（最大 200，与单页最大查询数对齐）；</li>
 *   <li>status 必填，复用 {@link FeedbackStatus}；</li>
 *   <li>handlerReply 可选；同一批所有反馈共用同一条回复（运营场景下批量场景一般不写回复，可空）。</li>
 * </ul>
 */
@Data
public class BatchUpdateFeedbackStatusRequest {

    /** 反馈 id 列表，必填非空，最大长度 200 */
    private List<Long> ids;

    /** 目标状态，必填 */
    private FeedbackStatus status;

    /** 管理员回复，可选；最长 500 字 */
    private String handlerReply;
}
