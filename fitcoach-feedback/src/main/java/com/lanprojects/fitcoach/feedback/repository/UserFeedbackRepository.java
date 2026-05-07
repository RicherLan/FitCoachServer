package com.lanprojects.fitcoach.feedback.repository;

import com.lanprojects.fitcoach.feedback.entity.UserFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 反馈数据访问层。
 * <p>当前只用基础 CRUD；后续运营后台需要按 uid / type / 时间区间查询时再补 derived query。
 */
public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {
}
