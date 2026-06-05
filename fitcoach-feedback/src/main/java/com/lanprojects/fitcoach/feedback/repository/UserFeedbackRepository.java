package com.lanprojects.fitcoach.feedback.repository;

import com.lanprojects.fitcoach.feedback.entity.FeedbackStatus;
import com.lanprojects.fitcoach.feedback.entity.FeedbackType;
import com.lanprojects.fitcoach.feedback.entity.UserFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 反馈数据访问层。
 * <p>
 * 客户端侧只用基础 CRUD；后台管理（fitcoach-admin）通过
 * {@link JpaSpecificationExecutor} 做"按 status / type / 关键字 / 时间范围" 的动态组合分页查询。
 */
public interface UserFeedbackRepository
        extends JpaRepository<UserFeedback, Long>, JpaSpecificationExecutor<UserFeedback> {

    /** 按状态分页（admin Dashboard / 列表页快捷过滤用） */
    Page<UserFeedback> findByStatus(FeedbackStatus status, Pageable pageable);

    /** 按类型分页 */
    Page<UserFeedback> findByType(FeedbackType type, Pageable pageable);

    /** 状态计数（admin Dashboard 用） */
    long countByStatus(FeedbackStatus status);

    /** 类型计数（admin Dashboard 用） */
    long countByType(FeedbackType type);

    /** 按用户 uid 计数（admin 用户详情显示该用户的反馈数） */
    long countByUid(String uid);

    /** 按用户 uid 分页查询（客户端"我的反馈"列表） */
    Page<UserFeedback> findByUidOrderByCreatedAtDesc(String uid, Pageable pageable);
}
