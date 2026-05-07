package com.lanprojects.fitcoach.feedback.service;

import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.upload.UploadProperties;
import com.lanprojects.fitcoach.feedback.dto.CreateFeedbackRequest;
import com.lanprojects.fitcoach.feedback.dto.FeedbackResponse;
import com.lanprojects.fitcoach.feedback.dto.UploadAttachmentResponse;
import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import com.lanprojects.fitcoach.feedback.entity.UserFeedback;
import com.lanprojects.fitcoach.feedback.repository.UserFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 反馈业务核心服务：
 * <ul>
 *   <li>{@link #uploadAttachment(String, MultipartFile)}：单张附件落盘 + 校验；</li>
 *   <li>{@link #createFeedback(String, CreateFeedbackRequest)}：正文 + 附件 URL 列表入库。</li>
 * </ul>
 *
 * <p>校验严格但只用 BusinessException + ResultCode，不抛 RuntimeException，
 * 与项目其它模块（UserProfileService 等）风格一致，
 * 错误最终被 GlobalExceptionHandler 统一转成 {@code Result.error}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final UploadProperties uploadProperties;
    private final FeedbackAttachmentStorageService attachmentStorageService;
    private final UserFeedbackRepository feedbackRepository;

    /**
     * 上传一张反馈附件并返回 URL。
     * <p>校验顺序：file 非空 → contentType 白名单 → size。
     * 三类失败码按 6101/6103/6102 严格区分，方便客户端做精准提示。
     */
    public UploadAttachmentResponse uploadAttachment(String uid, MultipartFile file) {
        UploadProperties.Feedback cfg = uploadProperties.getFeedback();

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FEEDBACK_ATTACHMENT_FILE_EMPTY);
        }
        String contentType = file.getContentType();
        if (contentType == null
                || !cfg.getAllowedContentTypesView().contains(contentType.toLowerCase(Locale.ROOT).trim())) {
            log.warn("反馈附件 contentType 不在白名单, uid={}, contentType={}", uid, contentType);
            throw new BusinessException(ResultCode.FEEDBACK_ATTACHMENT_CONTENT_TYPE_INVALID);
        }
        if (file.getSize() > cfg.getMaxSizeBytes()) {
            log.warn("反馈附件超大, uid={}, size={}, max={}", uid, file.getSize(), cfg.getMaxSizeBytes());
            throw new BusinessException(ResultCode.FEEDBACK_ATTACHMENT_FILE_TOO_LARGE);
        }

        String url = attachmentStorageService.saveAttachment(uid, file);
        return new UploadAttachmentResponse(url);
    }

    /**
     * 创建反馈记录。
     * <p>校验：type 必填、content 非空 + 长度上限、附件数量上限、附件 URL 必须以 url-prefix 开头（防止伪造任意外链）。
     */
    @Transactional
    public FeedbackResponse createFeedback(String uid, CreateFeedbackRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求体为空");
        }
        UploadProperties.Feedback cfg = uploadProperties.getFeedback();

        if (request.getType() == null) {
            throw new BusinessException(ResultCode.FEEDBACK_TYPE_INVALID);
        }
        String content = request.getContent();
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ResultCode.FEEDBACK_CONTENT_EMPTY);
        }
        // 用 codePointCount 而非 length() — emoji 等代理对算 1 个字符，与客户端字数统计对齐
        int len = content.codePointCount(0, content.length());
        if (len > cfg.getMaxContentLength()) {
            throw new BusinessException(ResultCode.FEEDBACK_CONTENT_TOO_LONG);
        }

        List<String> attachmentUrls = request.getAttachmentUrls() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getAttachmentUrls());
        if (attachmentUrls.size() > cfg.getMaxAttachmentCount()) {
            throw new BusinessException(ResultCode.FEEDBACK_ATTACHMENT_TOO_MANY);
        }
        // 防伪：附件 URL 必须以本服务的 url-prefix 开头，杜绝外站链接被塞进来
        String prefix = trimTrailingSlash(uploadProperties.getUrlPrefix());
        String expectedSubPath = "/" + cfg.getSubDir() + "/";
        for (String url : attachmentUrls) {
            if (url == null || url.isBlank()
                    || !url.startsWith(prefix)
                    || !url.contains(expectedSubPath)) {
                log.warn("反馈附件 URL 校验失败, uid={}, url={}, prefix={}", uid, url, prefix);
                throw new BusinessException(ResultCode.FEEDBACK_ATTACHMENT_URL_INVALID);
            }
        }

        UserFeedback entity = new UserFeedback();
        entity.setUid(uid);
        entity.setType(request.getType());
        entity.setContent(content.trim());
        entity.setAttachmentUrls(attachmentUrls);
        entity.setAppVersion(request.getAppVersion());
        entity.setPlatform(request.getPlatform());
        // 后台处理状态默认 PENDING，admin 模块后续可改为 PROCESSING / RESOLVED / IGNORED
        entity.setStatus(FeedbackStatus.PENDING);
        UserFeedback saved = feedbackRepository.save(entity);
        log.info("反馈创建成功, uid={}, id={}, type={}, attachments={}",
                uid, saved.getId(), saved.getType(), attachmentUrls.size());
        return FeedbackResponse.from(saved);
    }

    private String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
