package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.payment.AdminPaymentOrderDto;
import com.lanprojects.fitcoach.admin.dto.payment.AdminRefundRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.client.AppFlavor;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.common.util.CsvHttpResponseUtil;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.payment.entity.OrderStatus;
import com.lanprojects.fitcoach.payment.entity.PaymentOrder;
import com.lanprojects.fitcoach.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 支付订单管理（admin 后台）。
 *
 * <p>路径前缀：/api/admin/payment/orders
 * <ul>
 *   <li>{@code GET /} —— 分页列表，可按 status / flavor 过滤（flavor 阶段 4 波 2 新增，见 {@link #parseFlavorFilter}）</li>
 *   <li>{@code GET /{orderId}} —— 详情</li>
 *   <li>{@code POST /{orderId}/refund} —— 标记退款（V1 仅记账，钱原路退由财务线下处理）</li>
 * </ul>
 */
@Slf4j
@Tag(name = "后台-支付订单", description = "订单列表/详情/退款（V1 退款仅记账）")
@RestController
@RequestMapping("/api/admin/payment/orders")
@RequiredArgsConstructor
public class AdminPaymentOrderController {

    /** 单次 CSV 导出最大订单条数，避免拖垮 DB */
    private static final int MAX_EXPORT_SIZE = 10_000;

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentService paymentService;
    private final UserRepository userRepository;
    private final AdminAuditLogService auditLogService;

    /**
     * 订单分页（按创建时间倒序）。可选 status / flavor 过滤。
     *
     * <p><b>flavor 参数取值</b>：
     * <ul>
     *   <li>{@code CN} —— 只看国内包下的订单；</li>
     *   <li>{@code GLOBAL} —— 只看海外包下的订单；</li>
     *   <li>{@code UNKNOWN} —— 只看 app_flavor 为 null 的历史订单 / Postman 手工造单；</li>
     *   <li>缺省 —— 全量。</li>
     * </ul>
     */
    @GetMapping
    public Result<PageResponse<AdminPaymentOrderDto>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "flavor", required = false) String flavor) {
        // 1-based → 0-based
        int p = Math.max(page, 1) - 1;
        int s = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(p, s);

        Page<PaymentOrder> orderPage = queryOrders(status, flavor, pageable);

        // join user 信息（少量 user，单次 in 查询）
        Map<Long, User> userMap = batchLoadUsers(orderPage.getContent());
        return Result.success(PageResponse.from(orderPage, o -> enrichWithUser(o, userMap)));
    }

    /** 订单详情 */
    @GetMapping("/{orderId}")
    public Result<AdminPaymentOrderDto> detail(@PathVariable("orderId") String orderId) {
        PaymentOrder order = paymentService.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));
        Map<Long, User> userMap = batchLoadUsers(List.of(order));
        return Result.success(enrichWithUser(order, userMap));
    }

    /**
     * 标记退款 — V1 仅记账，不真实调用通道退款。
     * <p>调用后订单 status → REFUNDED，refundStatus → COMPLETED；会员状态不会自动撤销，
     * 如需撤销请操作员到 {@link AdminUserMembershipController#revoke} 单独撤销。
     */
    @PostMapping("/{orderId}/refund")
    public Result<AdminPaymentOrderDto> refund(
            HttpServletRequest request,
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody AdminRefundRequest body) {
        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        // P1-18：先尝试退款；成功落审计，失败也落 FAILED 审计便于事后追溯
        try {
            PaymentOrder updated = paymentService.adminMarkRefunded(orderId, body.getRefundCents(), body.getReason());
            log.info("[admin] {} 给订单 {} 标记退款（amount={}, reason={}）",
                    operator, orderId, body.getRefundCents(), body.getReason());
            String summary = String.format("refund cents=%s, reason=%s",
                    body.getRefundCents() == null ? "FULL" : body.getRefundCents(),
                    body.getReason());
            auditLogService.logSuccess(request, AdminAuditAction.REFUND_ORDER, "ORDER", orderId, summary);
            Map<Long, User> userMap = batchLoadUsers(List.of(updated));
            return Result.success(enrichWithUser(updated, userMap));
        } catch (RuntimeException e) {
            auditLogService.logFailure(request, AdminAuditAction.REFUND_ORDER, "ORDER", orderId,
                    String.format("refund cents=%s, reason=%s",
                            body.getRefundCents() == null ? "FULL" : body.getRefundCents(),
                            body.getReason()),
                    e.getMessage());
            throw e;
        }
    }

    /**
     * P2-12：按筛选条件导出订单 CSV（含 userUid/nickname/amount/status/refund 字段）。
     * <p>路径：{@code GET /api/admin/payment/orders/export}
     * <p>最多 {@link #MAX_EXPORT_SIZE} 条；超出请按 status 进一步过滤后再导。
     */
    @GetMapping("/export")
    public void exportCsv(HttpServletRequest request, HttpServletResponse response,
                          @RequestParam(value = "status", required = false) String status,
                          @RequestParam(value = "flavor", required = false) String flavor) throws IOException {
        Pageable pageable = PageRequest.of(0, MAX_EXPORT_SIZE,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        Page<PaymentOrder> orderPage = queryOrders(status, flavor, pageable);
        List<PaymentOrder> orders = orderPage.getContent();
        Map<Long, User> userMap = batchLoadUsers(orders);

        String operator = (String) request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USERNAME);
        auditLogService.logSuccess(request, AdminAuditAction.EXPORT_ORDERS, "ORDER", null,
                "rows=" + orders.size() + ", status=" + status + ", flavor=" + flavor);
        log.info("导出支付订单 CSV, operator={}, rows={}, status={}, flavor={}",
                operator, orders.size(), status, flavor);

        // 阶段 4 波 2：CSV 表头追加"市场"列，便于财务按 CN / GLOBAL 拆 GMV
        CsvHttpResponseUtil.write(response, "orders",
                List.of("订单号", "用户 uid", "用户昵称", "套餐", "金额(元)", "币种", "状态", "退款状态", "退款金额(元)",
                        "通道", "市场", "客户端", "通道单号", "创建时间", "支付时间", "退款时间", "失败原因"),
                orders, o -> {
                    User u = userMap.get(o.getUserId());
                    return List.of(
                            nullToEmpty(o.getOrderId()),
                            u == null ? "" : nullToEmpty(u.getUid()),
                            u == null ? "" : nullToEmpty(u.getNickname()),
                            nullToEmpty(o.getPlanSnapshotName()),
                            centsToYuan(o.getAmountCents()),
                            nullToEmpty(o.getCurrency()),
                            o.getStatus() == null ? "" : o.getStatus().name(),
                            o.getRefundStatus() == null ? "" : o.getRefundStatus().name(),
                            centsToYuan(o.getRefundAmountCents()),
                            o.getChannel() == null ? "" : o.getChannel().name(),
                            o.getAppFlavor() == null ? "" : o.getAppFlavor().name(),
                            nullToEmpty(o.getClientPlatform()),
                            nullToEmpty(o.getChannelTransactionId()),
                            fmtIso(o.getCreatedAt()),
                            fmtIso(o.getPaidAt()),
                            fmtIso(o.getRefundedAt()),
                            nullToEmpty(o.getFailReason())
                    );
                });
    }

    // ====== 内部 ======

    /**
     * list / exportCsv 共用的查询编排（阶段 4 波 2）—— 收敛 (status × flavor) 4 种组合的分派逻辑。
     *
     * <p>四象限：
     * <ol>
     *   <li>status 非空 + flavor 非空/UNKNOWN → {@code adminListByStatusAndFlavor}</li>
     *   <li>status 非空 + flavor 缺省       → {@code adminListByStatus}</li>
     *   <li>status 缺省  + flavor 非空/UNKNOWN → {@code adminListByFlavor}</li>
     *   <li>status 缺省  + flavor 缺省       → {@code adminList}</li>
     * </ol>
     */
    private Page<PaymentOrder> queryOrders(String status, String flavor, Pageable pageable) {
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasFlavor = flavor != null && !flavor.isBlank();

        if (hasStatus && hasFlavor) {
            return paymentService.adminListByStatusAndFlavor(parseStatus(status), parseFlavorFilter(flavor), pageable);
        } else if (hasStatus) {
            return paymentService.adminListByStatus(parseStatus(status), pageable);
        } else if (hasFlavor) {
            return paymentService.adminListByFlavor(parseFlavorFilter(flavor), pageable);
        } else {
            return paymentService.adminList(pageable);
        }
    }

    private OrderStatus parseStatus(String raw) {
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "status 参数不合法：" + raw);
        }
    }

    /**
     * 解析 flavor 筛选参数（阶段 4 波 2）：
     * <ul>
     *   <li>"CN" / "GLOBAL" → 对应枚举；</li>
     *   <li>"UNKNOWN" → 返回 {@code null} 匹配 app_flavor IS NULL；</li>
     *   <li>其他 → 400 参数不合法。</li>
     * </ul>
     */
    private AppFlavor parseFlavorFilter(String raw) {
        String v = raw.trim().toUpperCase();
        if ("UNKNOWN".equals(v)) return null;
        try {
            return AppFlavor.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "flavor 参数不合法：" + raw + "（合法值：CN / GLOBAL / UNKNOWN）");
        }
    }

    /**
     * 批量根据 userId 查 User，避免每条订单都触发一次查库。
     * <p>orderRepository 没有 in 查询，借助 user_id 去 fitcoach-login 的 UserRepository。
     */
    private Map<Long, User> batchLoadUsers(List<PaymentOrder> orders) {
        Set<Long> userIds = orders.stream().map(PaymentOrder::getUserId).collect(Collectors.toSet());
        if (userIds.isEmpty()) return new HashMap<>();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private AdminPaymentOrderDto enrichWithUser(PaymentOrder o, Map<Long, User> userMap) {
        AdminPaymentOrderDto dto = AdminPaymentOrderDto.from(o);
        User u = userMap.get(o.getUserId());
        if (u != null) {
            dto.setUserUid(u.getUid());
            dto.setUserNickname(u.getNickname());
        }
        return dto;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 分 → 元，保留 2 位小数；null → 空 */
    private static String centsToYuan(Integer cents) {
        if (cents == null) return "";
        return String.format("%.2f", cents / 100.0);
    }

    private static String fmtIso(LocalDateTime t) {
        return t == null ? "" : t.format(ISO_FMT);
    }
}
