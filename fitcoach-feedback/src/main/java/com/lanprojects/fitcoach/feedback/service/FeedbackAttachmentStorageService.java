package com.lanprojects.fitcoach.feedback.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 反馈附件存储抽象。
 * <p>
 * 与 {@code AvatarStorageService} 一致的设计：
 * 本期落本地磁盘走静态资源访问，后期切 OSS / S3 仅需新增实现 + 改 yml，业务层无感。
 */
public interface FeedbackAttachmentStorageService {

    /**
     * 保存一个附件并返回可被客户端访问的 URL（默认相对路径，由客户端拼 baseURL）。
     *
     * @param uid  上传者 uid，用于文件名前缀 + 排查
     * @param file 已通过基础校验（非空 / contentType / size）的附件
     * @return 形如 {@code /static/feedback/202506/uid_xxx.jpg} 的可访问 URL
     */
    String saveAttachment(String uid, MultipartFile file);
}
