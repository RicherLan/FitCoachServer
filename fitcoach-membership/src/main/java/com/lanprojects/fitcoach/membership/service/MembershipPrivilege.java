package com.lanprojects.fitcoach.membership.service;

/**
 * 会员特权枚举 — 业务方调 {@link MembershipService#hasPrivilege(Long, MembershipPrivilege)} 检查。
 *
 * <p><b>设计意图</b>：把"具体能做什么"从"是否会员"中解耦。MVP 只有一项 {@link #UNLOCK_PAID_EXERCISES}，
 * 当前所有套餐（日卡/周卡/月卡/季卡/年卡）都拥有所有特权——所以暂不需要在 plan 表加"特权列表"字段。
 *
 * <p>未来扩展（不改模型）：
 * <ul>
 *   <li>新增特权值 → 在此 enum 加；</li>
 *   <li>分套餐授予特权 → 在 MembershipPlan 加 privilegeJson 字段（"YEARLY 才有 AI 教练"）；</li>
 *   <li>调用方代码不变（一直都是 hasPrivilege(userId, PRIV)）。</li>
 * </ul>
 */
public enum MembershipPrivilege {
    /** 解锁所有付费动作（is_free=false 的动作） */
    UNLOCK_PAID_EXERCISES,

    /** 预留：AI 教练问答（年卡专属，未来用） */
    AI_COACH_CHAT,

    /** 预留：训练计划生成（未来用） */
    TRAINING_PLAN_GENERATION,

    /** 预留：训练数据导出 CSV（未来用） */
    DATA_EXPORT
}
