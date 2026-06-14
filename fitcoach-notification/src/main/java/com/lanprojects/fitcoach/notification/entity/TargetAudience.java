package com.lanprojects.fitcoach.notification.entity;

/**
 * 系统通知的投放对象类型。
 *
 * <ul>
 *   <li>{@link #ALL} —— 所有登录用户（包括未来注册的新用户，只要本通知还在有效期内）</li>
 *   <li>{@link #SPECIFIC_USERS} —— 仅 {@code target_uids} 列表内的 uid 能命中</li>
 * </ul>
 *
 * <p>不引入"按 tag/角色/平台"等更复杂维度 —— 本期只满足"全员通告 + 给特定一个人发"两个核心场景。
 */
public enum TargetAudience {
    ALL,
    SPECIFIC_USERS,
}
