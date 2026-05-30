package com.lanprojects.fitcoach.admin.audit;

import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.audit.AdminAuditPort;
import com.lanprojects.fitcoach.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 后台高危操作审计日志写入服务。
 *
 * <p>**设计准则**：
 * <ul>
 *   <li>异步落库（{@link Async}） —— 审计日志不能因为 IO 抖动拖慢业务接口；</li>
 *   <li>所有写入路径全部 try/catch —— 哪怕 DB 失联也只是丢一条审计，不可冒泡到业务；</li>
 *   <li>不在这里组装 summary —— 由调用方根据语义自行拼接，避免审计层反向依赖业务 entity；</li>
 *   <li>失败记录（{@link #logFailure}）由 controller 在业务抛异常的 catch 分支显式调用，
 *       这样 server 维护者一眼能在代码里看见哪些路径会留审计痕迹。</li>
 * </ul>
 *
 * <p>**敏感字段警告**：调用方在拼 summary 时，严禁带入密码、token、完整手机号、
 * 银行卡号等敏感字段；如必须脱敏，可使用 {@link com.lanprojects.fitcoach.common.util.LogUtils}。
 *
 * <p>使用示例：
 * <pre>{@code
 * auditLogService.logSuccess(request,
 *         AdminAuditAction.REFUND_ORDER,
 *         "ORDER", orderId,
 *         String.format("refund %d cents, reason=%s", body.getRefundCents(), body.getReason()));
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditLogService implements AdminAuditPort {

    private static final int MAX_SUMMARY = 1024;
    private static final int MAX_ERROR = 512;
    private static final int MAX_UA = 256;
    private static final int MAX_URI = 256;

    private final AdminAuditLogRepository repository;

    /** 成功路径：业务执行完毕后调用。 */
    public void logSuccess(HttpServletRequest request,
                           AdminAuditAction action,
                           String targetType,
                           String targetId,
                           String summary) {
        write(request, null, null, action, targetType, targetId, summary, true, null);
    }

    /** 失败路径：catch 到异常时调用；不会再抛出异常。 */
    public void logFailure(HttpServletRequest request,
                           AdminAuditAction action,
                           String targetType,
                           String targetId,
                           String summary,
                           String errorMsg) {
        write(request, null, null, action, targetType, targetId, summary, false, errorMsg);
    }

    /**
     * 显式指定 username / role 的成功路径 —— 用于登录场景：
     * 此时 {@link AdminAuthInterceptor} 已 excludePathPatterns 放行登录接口，
     * request attribute 中没有 username，必须由调用方从登录 body 中取出再传过来。
     */
    public void logSuccessAs(HttpServletRequest request,
                             String username,
                             String role,
                             AdminAuditAction action,
                             String targetType,
                             String targetId,
                             String summary) {
        write(request, username, role, action, targetType, targetId, summary, true, null);
    }

    /** 显式指定 username / role 的失败路径 —— 用于登录失败审计（用户名错也要落账）。 */
    public void logFailureAs(HttpServletRequest request,
                             String username,
                             String role,
                             AdminAuditAction action,
                             String targetType,
                             String targetId,
                             String summary,
                             String errorMsg) {
        write(request, username, role, action, targetType, targetId, summary, false, errorMsg);
    }

    // =====================================================================
    // AdminAuditPort 实现：让非 admin 模块（fitcoach-log 等）也能落审计。
    // String 形式的 action 会反向解析为 AdminAuditAction 枚举；解析失败时直接打 warn，
    // 不入库（避免脏数据），因为合法的 action 应该在 AdminAuditAction 枚举中先声明。
    // =====================================================================

    @Override
    public void logSuccess(HttpServletRequest request, String action,
                           String targetType, String targetId, String summary) {
        AdminAuditAction enumAction = parseAction(action);
        if (enumAction == null) return;
        write(request, null, null, enumAction, targetType, targetId, summary, true, null);
    }

    @Override
    public void logFailure(HttpServletRequest request, String action,
                           String targetType, String targetId, String summary, String errorMsg) {
        AdminAuditAction enumAction = parseAction(action);
        if (enumAction == null) return;
        write(request, null, null, enumAction, targetType, targetId, summary, false, errorMsg);
    }

    private static AdminAuditAction parseAction(String raw) {
        if (raw == null || raw.isBlank()) {
            log.warn("[audit] reject empty action from port caller");
            return null;
        }
        try {
            return AdminAuditAction.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[audit] unknown action='{}' from port caller, skip", raw);
            return null;
        }
    }

    /**
     * 实际异步落库。
     * <p>{@link Async} 让审计写入跑在独立线程，业务接口立即返回；
     * 抛错只打 warn 日志，绝不冒泡到业务调用方。
     *
     * <p>这里读 {@link HttpServletRequest} 的 attribute 必须在异步任务"调度前"完成 ——
     * 异步线程里 request 可能已 recycle。因此本方法在进入异步之前先把所有 String 字段拷出来。
     */
    private void write(HttpServletRequest request,
                       String overrideUsername,
                       String overrideRole,
                       AdminAuditAction action,
                       String targetType,
                       String targetId,
                       String summary,
                       boolean success,
                       String errorMsg) {
        // 在同步线程内提取 request 信息（异步线程内 request 已失效）
        String username = overrideUsername != null
                ? overrideUsername
                : stringAttr(request, AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        String role = overrideRole != null
                ? overrideRole
                : stringAttr(request, AdminAuthInterceptor.ATTR_ADMIN_ROLE);
        String ip = request == null ? null : ClientIpResolver.resolve(request);
        String ua = request == null ? null : request.getHeader("User-Agent");
        String uri = request == null ? null
                : (request.getMethod() + " " + request.getRequestURI());

        AdminAuditLog log = new AdminAuditLog();
        log.setAdminUsername(username == null ? "(unknown)" : username);
        log.setAdminRole(role);
        log.setAction(action);
        log.setTargetType(safeTrunc(targetType, 32));
        log.setTargetId(safeTrunc(targetId, 128));
        log.setSummary(safeTrunc(summary, MAX_SUMMARY));
        log.setSuccess(success);
        log.setErrorMsg(safeTrunc(errorMsg, MAX_ERROR));
        log.setIp(safeTrunc(ip, 64));
        log.setUa(safeTrunc(ua, MAX_UA));
        log.setRequestUri(safeTrunc(uri, MAX_URI));

        persistAsync(log);
    }

    @Async("auditLogExecutor")
    public void persistAsync(AdminAuditLog log) {
        try {
            repository.save(log);
        } catch (Exception e) {
            // 审计落库失败不影响业务，只打 warn
            AdminAuditLogService.log.warn(
                    "[audit] save failed, action={}, target={}/{}, err={}",
                    log.getAction(), log.getTargetType(), log.getTargetId(), e.getMessage());
        }
    }

    private static String stringAttr(HttpServletRequest request, String key) {
        if (request == null) return null;
        Object v = request.getAttribute(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String safeTrunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
