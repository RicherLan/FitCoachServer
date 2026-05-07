package com.lanprojects.fitcoach.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单张附件上传响应。
 * <p>客户端拿到 url 后塞进自己的本地 attachmentUrls 列表，提交反馈正文时一起带上。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadAttachmentResponse {

    /** 服务端给出的可访问 URL（默认相对路径，由客户端拼 baseURL） */
    private String url;
}
