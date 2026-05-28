package com.lanprojects.fitcoach.controller.payment;

import com.lanprojects.fitcoach.common.client.ClientContext;
import com.lanprojects.fitcoach.common.model.Result;
import com.lanprojects.fitcoach.controller.payment.dto.CreateOrderRequest;
import com.lanprojects.fitcoach.controller.payment.dto.CreateOrderResponse;
import com.lanprojects.fitcoach.controller.payment.dto.PaymentOrderDTO;
import com.lanprojects.fitcoach.login.support.AuthSupport;
import com.lanprojects.fitcoach.membership.entity.MembershipPlan;
import com.lanprojects.fitcoach.membership.service.MembershipService;
import com.lanprojects.fitcoach.payment.entity.PaymentOrder;
import com.lanprojects.fitcoach.payment.provider.wechat.WeChatCallbackHandler;
import com.lanprojects.fitcoach.payment.service.CreateOrderCommand;
import com.lanprojects.fitcoach.payment.service.PaymentService;
import com.lanprojects.fitcoach.payment.service.PlanSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付控制器（用户端） — 编排 membership + payment 两个领域服务。
 *
 * <p>**为什么放在 fitcoach-app 而非 fitcoach-payment 模块下**：
 * <ul>
 *   <li>下单接口需要"查 plan + 创建订单"两步，跨 membership 和 payment 两个模块；</li>
 *   <li>Phase 1 设计明确 payment 不依赖 membership（保持单向 + 事件解耦），所以编排只能在 fitcoach-app 这层；</li>
 *   <li>fitcoach-app 已经依赖了所有业务模块，是天然的组合层。</li>
 * </ul>
 *
 * <p>接口前缀：/api/payment
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final MembershipService membershipService;
    private final PaymentService paymentService;
    private final WeChatCallbackHandler weChatCallbackHandler;
    private final AuthSupport auth;

    // ====== 创建订单 ======

    /**
     * 创建支付订单。
     *
     * <p>流程：
     * <ol>
     *   <li>查 plan（必须启用），转 PlanSnapshot；</li>
     *   <li>从 ClientContext 取 platform（X-Client-Platform Header），作为通道路由依据；</li>
     *   <li>调 PaymentService.createOrder 落库 PENDING + 调 Provider 创建通道侧订单；</li>
     *   <li>MOCK 通道下单即视为支付成功，立即触发会员激活事件；</li>
     *   <li>响应客户端拉起 SDK 所需 payload。</li>
     * </ol>
     */
    @PostMapping("/order")
    public Result<CreateOrderResponse> createOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateOrderRequest req,
            HttpServletRequest httpRequest) {
        Long userId = auth.requireUserId(authorization);

        // 1. 查 plan + 转 snapshot（snapshot 是 payment 模块对外的 plan 契约，避免反向依赖 membership）
        MembershipPlan plan = membershipService.findEnabledPlanByCode(req.getPlanCode());
        PlanSnapshot snapshot = new PlanSnapshot(
                plan.getPlanCode(),
                plan.getDisplayName(),
                plan.getPriceCny(),
                plan.getPriceUsdCents()
        );

        // 2. 平台 + IP（微信支付必填 spbill_create_ip；后续 ApplePaymentProvider 不关心）
        String platform = ClientContext.platform();
        String clientIp = resolveClientIp(httpRequest);

        // 3. 下单
        PaymentService.CreateOrderResponse svcResp = paymentService.createOrder(
                new CreateOrderCommand(userId, snapshot, req.getChannel(), platform, clientIp));

        return Result.success(CreateOrderResponse.builder()
                .orderId(svcResp.orderId())
                .channel(svcResp.channel())
                .amountCents(svcResp.amountCents())
                .currency(svcResp.currency())
                .clientPayload(svcResp.clientPayload() != null ? svcResp.clientPayload() : new HashMap<>())
                .immediatelyPaid(svcResp.immediatelyPaid())
                .build());
    }

    // ====== 查询订单 ======

    /**
     * 拿单个订单详情（必须是当前用户的，否则返回 8108 PAYMENT_ORDER_NOT_OWNED）。
     */
    @GetMapping("/order/{orderId}")
    public Result<PaymentOrderDTO> orderDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("orderId") String orderId) {
        Long userId = auth.requireUserId(authorization);
        PaymentOrder order = paymentService.requireOrderForUser(orderId, userId);
        return Result.success(PaymentOrderDTO.from(order));
    }

    /**
     * 用户取消订单（仅 PENDING 可取消，幂等）。
     */
    @PostMapping("/order/{orderId}/cancel")
    public Result<Void> cancelOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("orderId") String orderId) {
        Long userId = auth.requireUserId(authorization);
        // 校验订单归属（避免 A 取消 B 的订单）
        paymentService.requireOrderForUser(orderId, userId);
        paymentService.closeOrder(orderId, "用户主动取消");
        return Result.success();
    }

    /**
     * 当前用户的订单列表（分页，按创建时间倒序）。
     */
    @GetMapping("/orders/my")
    public Result<Page<PaymentOrderDTO>> myOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Long userId = auth.requireUserId(authorization);
        // 分页参数兜底（避免恶意翻太大）
        size = Math.min(Math.max(size, 1), 50);
        page = Math.max(page, 0);
        Pageable pageable = PageRequest.of(page, size);
        return Result.success(paymentService.listMyOrders(userId, pageable).map(PaymentOrderDTO::from));
    }

    // ====== 通道回调 ======

    /**
     * Mock 通道"前端模拟回调"接口 — 仅在 MOCK 模式启用时使用，便于联调。
     *
     * <p>真实回调走 {@code /api/payment/notify/wechat} 或 Apple 服务端凭证校验，不走这里。
     *
     * <p>**安全**：本接口本身不做签名校验，必须依赖 SysConfig 的 {@code payment.mock.enabled=true} 开关，
     * 上线前必须把开关关掉，否则任何人调一下就能给自己开通会员。
     */
    @PostMapping("/notify/mock")
    public Result<Map<String, Object>> mockNotify(
            @RequestParam("orderId") String orderId) {
        log.warn("[payment] 收到 mock 回调 orderId={}（仅开发期使用）", orderId);
        paymentService.markPaid(orderId, "MOCK_PREPAY_" + orderId, "MOCK_TXN_" + orderId);
        return Result.success(Map.of("ok", true, "orderId", orderId));
    }

    /**
     * 微信支付 V3 回调 — 签名校验 + AES-256-GCM 解密 + 标记订单支付成功。
     *
     * <p><b>安全措施</b>：
     * <ul>
     *   <li>Wechatpay-Signature 签名校验（MVP 阶段先跳过，生产环境需配置微信平台证书）；</li>
     *   <li>AES-256-GCM 对称解密 resource 数据（apiV3Key 作为密钥）；</li>
     *   <li>markPaid 内部幂等（重复调用安全）。</li>
     * </ul>
     *
     * <p><b>返回值</b>：直接返回 {@code Map}（不包 {@code Result}），
     * 因为微信要求的格式是 {@code {"code":"SUCCESS","message":"OK"}}，不是本系统的标准包装。
     *
     * @see <a href="https://pay.weixin.qq.com/docs/merchant/apis/in-app-payment/payment-notice.html">
     *     微信支付 V3 回调通知文档</a>
     */
    @PostMapping("/notify/wechat")
    public Map<String, String> wechatNotify(
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            @RequestBody(required = false) String body) {

        boolean success = weChatCallbackHandler.handleCallback(
                timestamp, nonce, signature, serial, body);

        if (success) {
            return Map.of("code", "SUCCESS", "message", "OK");
        } else {
            // 返回 FAIL 让微信重试（微信会按策略重试最多 15 次）
            return Map.of("code", "FAIL", "message", "处理失败，请重试");
        }
    }

    // ====== 内部 ======

    /**
     * 取客户端真实 IP — 优先 X-Forwarded-For（反向代理场景），fallback 到 remoteAddr。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // XFF 可能是 "client, proxy1, proxy2"，取第一个
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }
}
