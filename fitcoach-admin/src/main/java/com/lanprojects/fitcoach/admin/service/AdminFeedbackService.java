package com.lanprojects.fitcoach.admin.service;

import com.lanprojects.fitcoach.admin.dto.FeedbackDetailDto;
import com.lanprojects.fitcoach.admin.dto.FeedbackSummaryDto;
import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.UpdateFeedbackStatusRequest;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import com.lanprojects.fitcoach.feedback.entity.FeedbackType;
import com.lanprojects.fitcoach.feedback.entity.UserFeedback;
import com.lanprojects.fitcoach.feedback.repository.UserFeedbackRepository;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员侧反馈管理 Service。
 * <p>
 * 关键能力：
 * <ul>
 *   <li>分页 + 多条件（状态 / 类型 / 关键字 / 时间范围）查询</li>
 *   <li>详情（含完整 content / 附件列表 / 提交者头像）</li>
 *   <li>状态流转（写 status + handlerAdmin + handlerReply + handledAt）</li>
 * </ul>
 * <p>
 * 性能要点：列表页关联 nickname 不走 N 次 select —— 收集本页 uid 后一次性批量查 user 表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFeedbackService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_REPLY_LENGTH = 500;

    private final UserFeedbackRepository userFeedbackRepository;
    private final UserRepository userRepository;
    private final AdminUrlService adminUrlService;

    /**
     * 分页查询反馈列表
     *
     * @param status   状态过滤，可选
     * @param type     类型过滤，可选
     * @param keyword  按 content / uid 模糊匹配，可选
     * @param startMs  创建时间起（毫秒，含），可选
     * @param endMs    创建时间止（毫秒，不含），可选
     */
    public PageResponse<FeedbackSummaryDto> listFeedbacks(int page, int size,
                                                          FeedbackStatus status,
                                                          FeedbackType type,
                                                          String keyword,
                                                          Long startMs, Long endMs) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1) - 1;
        Specification<UserFeedback> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                ps.add(cb.equal(root.get("type"), type));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                ps.add(cb.or(
                        cb.like(root.get("content"), like),
                        cb.like(root.get("uid"), like)
                ));
            }
            if (startMs != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), toLocal(startMs)));
            }
            if (endMs != null) {
                ps.add(cb.lessThan(root.get("createdAt"), toLocal(endMs)));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
        Page<UserFeedback> p = userFeedbackRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        // 批量取昵称：避免 N+1
        Map<String, String> uidToNickname = batchLoadNickname(p.getContent());
        return PageResponse.from(p, fb ->
                FeedbackSummaryDto.from(fb, uidToNickname.get(fb.getUid())));
    }

    /** 反馈详情 */
    public FeedbackDetailDto getFeedbackDetail(Long id) {
        UserFeedback fb = userFeedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.ADMIN_FEEDBACK_NOT_FOUND));
        User user = userRepository.findByUid(fb.getUid()).orElse(null);
        String nickname = user == null ? "" : user.getNickname();
        String avatar = adminUrlService.resolve(user == null ? null : user.getAvatarUrl());
        List<String> attachments = adminUrlService.resolveAll(fb.getAttachmentUrls());
        return FeedbackDetailDto.from(fb, nickname, avatar, attachments);
    }

    /**
     * 更新反馈状态 / 处理回复。
     * <p>状态可逆 — 不做"只能向前流转"限制（FeedbackStatus 注释里也明确允许误操作回退）。
     */
    @Transactional
    public FeedbackDetailDto updateStatus(Long id, UpdateFeedbackStatusRequest req, String operator) {
        if (req == null || req.getStatus() == null) {
            throw new BusinessException(ResultCode.ADMIN_FEEDBACK_STATUS_INVALID);
        }
        if (req.getHandlerReply() != null && req.getHandlerReply().length() > MAX_REPLY_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "处理回复长度不能超过 " + MAX_REPLY_LENGTH + " 字");
        }
        UserFeedback fb = userFeedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.ADMIN_FEEDBACK_NOT_FOUND));
        FeedbackStatus before = fb.getStatus();
        fb.setStatus(req.getStatus());
        // handlerReply：传入 null 表示不修改；传入 "" 表示清空
        if (req.getHandlerReply() != null) {
            fb.setHandlerReply(req.getHandlerReply().isBlank() ? null : req.getHandlerReply().trim());
        }
        fb.setHandlerAdmin(operator);
        fb.setHandledAt(LocalDateTime.now());
        userFeedbackRepository.save(fb);
        log.info("管理员变更反馈状态, operator={}, feedbackId={}, before={}, after={}",
                operator, id, before, req.getStatus());
        return getFeedbackDetail(id);
    }

    // ====== 内部 ======

    private LocalDateTime toLocal(long ms) {
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Map<String, String> batchLoadNickname(List<UserFeedback> list) {
        if (list == null || list.isEmpty()) return Map.of();
        Set<String> uids = new HashSet<>();
        for (UserFeedback fb : list) {
            if (fb.getUid() != null) uids.add(fb.getUid());
        }
        if (uids.isEmpty()) return Map.of();
        Map<String, String> map = new HashMap<>();
        // 一次 IN 查询解决 N+1，本页最多 size 个 uid，IN 列表完全可控
        userRepository.findByUidIn(uids).forEach(u -> map.put(u.getUid(), u.getNickname()));
        return map;
    }
}
