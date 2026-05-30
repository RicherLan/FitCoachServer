package com.lanprojects.fitcoach.admin.controller;

import com.lanprojects.fitcoach.admin.audit.AdminAuditAction;
import com.lanprojects.fitcoach.admin.audit.AdminAuditLogService;
import com.lanprojects.fitcoach.admin.dto.PageResponse;
import com.lanprojects.fitcoach.admin.dto.payment.AdminPaymentOrderDto;
import com.lanprojects.fitcoach.admin.dto.payment.AdminRefundRequest;
import com.lanprojects.fitcoach.admin.security.AdminAuthInterceptor;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.login.entity.User;
import com.lanprojects.fitcoach.login.repository.UserRepository;
import com.lanprojects.fitcoach.payment.entity.OrderStatus;
import com.lanprojects.fitcoach.payment.entity.PaymentOrder;
import com.lanprojects.fitcoach.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

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
 *   <li>{@code GET /} —— 分页列表，可按 status 过滤</li>
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

    private final PaymentService paymentService;
    private final UserRepository userRepository;
    private final AdminAuditLogService auditLogService;

    /** 订单分页（按创建时间倒序）。可选 status 过滤 */
    @GetMapping
    public Result<PageResponse<AdminPaymentOrderDto>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String status) {
        // 1-based → 0-based
        int p = Math.max(page, 1) - 1;
        int s = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(p, s);

        Page<PaymentOrder> orderPage;
        if (status != null && !status.isBlank()) {
            OrderStatus st = parseStatus(status);
            orderPage = paymentService.adminListByStatus(st, pageable);
        } else {
            orderPage = paymentService.adminList(pageable);
        }

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

    // ====== 内部 ======

    private OrderStatus parseStatus(String raw) {
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "status 参数不合法：" + raw);
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
}
