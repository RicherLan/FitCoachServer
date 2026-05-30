package com.lanprojects.fitcoach.common.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Admin 审计日志 SPI（轻量端口）。
 *
 * <p>定义在 fitcoach-common 中，目的是让 fitcoach-log 等非 admin 模块也能落审计日志，
 * 而不需要反向依赖 fitcoach-admin（避免循环依赖）。
 *
 * <p>真正的实现在 fitcoach-admin 模块的 AdminAuditLogService 上，由它实现本接口
 * 并以 Spring Bean 暴露；其它模块通过 {@code @Autowired(required = false)} 注入并使用。
 *
 * <p>方法以 String 而非枚举传 action，是为了让 fitcoach-common 与 admin 的 AdminAuditAction
 * 枚举解耦——任何模块都可以贡献自己的 action 字符串。约定：action 全大写，下划线分词。
 *
 * <p><b>关键不变量</b>：
 * <ul>
 *   <li>写操作必须异步、永不冒泡（实现内部 try/catch warn）；</li>
 *   <li>summary 严禁写入敏感字段（jwt secret、原文密码、token 等）；</li>
 *   <li>失败一律 warn 不影响业务调用方。</li>
 * </ul>
 */
public interface AdminAuditPort {

    /**
     * 记录一次操作成功。
     *
     * @param request    HTTP request，用于提取 ip / ua / uri / 当前管理员（实现自取 attribute）
     * @param action     大写下划线分词的动作名（如 "DELETE_LOG_TASK"）
     * @param targetType 目标实体类型（如 "LOG_TASK"）
     * @param targetId   目标实体 id（可为 null）
     * @param summary    简短摘要（不含敏感数据）
     */
    void logSuccess(HttpServletRequest request, String action, String targetType, String targetId, String summary);

    /**
     * 记录一次操作失败。
     *
     * @param request    HTTP request
     * @param action     大写下划线分词的动作名
     * @param targetType 目标实体类型
     * @param targetId   目标实体 id
     * @param summary    简短摘要
     * @param errorMsg   失败原因（建议来自 exception.getMessage()，实现层会截断到 512）
     */
    void logFailure(HttpServletRequest request, String action, String targetType, String targetId,
                    String summary, String errorMsg);
}
