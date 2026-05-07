package com.lanprojects.fitcoach.admin.entity;

/**
 * 管理员角色（粗粒度）。
 * <p>
 * 当前实现走"角色级"权限：所有 admin 接口都允许 SUPER_ADMIN / ADMIN 调用，
 * VIEWER 只允许 GET（查看）类接口，写接口（启用禁用、状态流转、改密）需要 ADMIN 及以上。
 * <p>
 * 后续如要做细粒度，可以在此基础上加 Permission 枚举 + 注解；
 * 当前规模下 3 个角色足够覆盖"超管 / 普通管理员 / 只读访客"3 类典型场景。
 */
public enum AdminRole {
    /** 超级管理员 — 拥有所有权限，可创建/删除其他管理员 */
    SUPER_ADMIN,
    /** 普通管理员 — 可读 + 可写业务数据（用户启用禁用、反馈状态变更） */
    ADMIN,
    /** 只读访客 — 仅可读所有列表 / 详情 / 统计，禁止任何写操作 */
    VIEWER;

    /** 是否允许写操作（PATCH/POST/PUT/DELETE 类接口校验用） */
    public boolean canWrite() {
        return this == SUPER_ADMIN || this == ADMIN;
    }
}
