package com.lanprojects.fitcoach.feedback.dto;

import com.lanprojects.fitcoach.feedback.entity.FeedbackType;
import lombok.Data;

import java.util.List;

/**
 * 创建反馈请求体。
 * <p>客户端先调 {@code POST /api/feedback/attachment} 上传单张附件得到 URL 列表，
 * 再以本对象提交正文 + 附件 URL 列表。
 * 这样拆分的理由：
 * <ul>
 *   <li>避免一次 multipart 携带多个大附件，超时/失败成本高；</li>
 *   <li>附件失败可重试单张，不影响已成功的；</li>
 *   <li>正文提交体积小，重试代价低。</li>
 * </ul>
 */
@Data
public class CreateFeedbackRequest {

    /** 反馈类型；为 null 时按 BAD_REQUEST 返回 */
    private FeedbackType type;

    /** 反馈正文 */
    private String content;

    /** 附件 URL 列表（已上传完成）；可空 */
    private List<String> attachmentUrls;

    /** 客户端版本号（如 1.2.3），可空 */
    private String appVersion;

    /** 平台标识（android / ios），可空 */
    private String platform;
}
