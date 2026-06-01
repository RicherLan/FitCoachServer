package com.lanprojects.fitcoach.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanprojects.fitcoach.common.event.PaymentSucceededEvent;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.payment.entity.OrderStatus;
import com.lanprojects.fitcoach.payment.entity.PaymentChannel;
import com.lanprojects.fitcoach.payment.entity.PaymentOrder;
import com.lanprojects.fitcoach.payment.provider.CreateOrderRequest;
import com.lanprojects.fitcoach.payment.provider.CreateOrderResult;
import com.lanprojects.fitcoach.payment.provider.PaymentChannelProvider;
import com.lanprojects.fitcoach.payment.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付核心服务 — 编排「下单 / 通道路由 / 落库 / 触发会员激活事件」。
 *
 * <p><b>关键流程：下单</b>
 * <ol>
 *   <li>查 plan 校验有效；</li>
 *   <li>路由器决定 / 校验通道可用；</li>
 *   <li>生成 orderId（业务订单号），先入库 PENDING；</li>
 *   <li>调 Provider 创建通道侧订单，得 prepay_id 等；</li>
 *   <li>更新 PaymentOrder 的 prepayId；</li>
 *   <li>若 Provider 返回 immediatelyPaid=true（Mock）→ 事务内调 markPaid；</li>
 *   <li>commit 后 markPaid 内的 publishEvent 才生效（@TransactionalEventListener AFTER_COMMIT）。</li>
 * </ol>
 *
 * <p><b>「PaymentService 不依赖 MembershipService」</b>：
 * Plan 详情由调用方（Controller / 上层服务）查好后通过 {@link PlanSnapshot} 传入，
 * 激活完全走 Spring 事件解耦——payment 模块只发事件，不知道「监听者是谁、是否激活成功」。
 * 这样 payment 和 membership 两个模块在编译期完全独立，互不感知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final DateTimeFormatter ORDER_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String CURRENCY_CNY = "CNY";
    private static final String CURRENCY_USD = "USD";

    private final PaymentOrderRepository orderRepository;
    private final PaymentChannelRouter channelRouter;
    private final ApplicationEventPublisher eventPublisher;
    // 复用 Spring 默认配置 ObjectMapper：序列化 attach 时确保 planCode 中的特殊字符
    // （引号 / 反斜线 / 控制字符）被正确转义，避免 JSON 注入污染微信回调解析。
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ====== 创建订单 ======

    /**
     * 创建支付订单（核心入口）。
     */
    @Transactional
    public CreateOrderResponse createOrder(CreateOrderCommand cmd) {
        PlanSnapshot plan = cmd.plan();
        if (plan == null || plan.planCode() == null || plan.priceCny() == null || plan.priceCny() <= 0) {
            throw new BusinessException(ResultCode.MEMBERSHIP_PLAN_PRICE_INVALID,
                    "下单套餐快照不完整或价格非法");
        }

        // 1. 路由通道
        PaymentChannel channel = cmd.channel() != null
                ? cmd.channel()
                : channelRouter.resolveDefault(cmd.clientPlatform());
        PaymentChannelProvider provider = channelRouter.require(channel);

        // 2. 计算金额 + 币种（按通道决定币种，非常关键 —— 微信只能 CNY，Apple 必须本地币）
        int amountCents;
        String currency;
        if (channel == PaymentChannel.APPLE_IAP || channel == PaymentChannel.GOOGLE_PLAY) {
            if (plan.priceUsdCents() == null || plan.priceUsdCents() <= 0) {
                throw new BusinessException(ResultCode.PAYMENT_CONFIG_MISSING,
                        "套餐 " + plan.planCode() + " 缺少海外价格配置（priceUsdCents）");
            }
            amountCents = plan.priceUsdCents();
            currency = CURRENCY_USD;
        } else {
            amountCents = plan.priceCny();
            currency = CURRENCY_CNY;
        }

        // 3. 生成订单号 + 落库 PENDING
        String orderId = generateOrderId(cmd.userId());
        PaymentOrder order = new PaymentOrder();
        order.setOrderId(orderId);
        order.setUserId(cmd.userId());
        order.setPlanCode(plan.planCode());
        order.setPlanSnapshotName(plan.displayName());
        order.setChannel(channel);
        order.setClientPlatform(cmd.clientPlatform());
        order.setAmountCents(amountCents);
        order.setCurrency(currency);
        order.setStatus(OrderStatus.PENDING);
        order = orderRepository.save(order);
        log.info("[payment] 创建订单 orderId={} userId={} planCode={} channel={} amountCents={} currency={}",
                orderId, cmd.userId(), plan.planCode(), channel, amountCents, currency);

        // 4. 调 Provider 创建通道侧订单
        CreateOrderRequest req = new CreateOrderRequest(
                orderId,
                cmd.userId(),
                plan.planCode(),
                plan.displayName(),
                amountCents,
                currency,
                cmd.clientPlatform(),
                cmd.clientIp(),
                buildAttachJson(cmd.userId(), plan.planCode())
        );
        CreateOrderResult providerResult = provider.createOrder(req);

        // 5. 更新订单 prepayId
        if (providerResult.prepayId() != null) {
            order.setChannelPrepayId(providerResult.prepayId());
            orderRepository.save(order);
        }

        // 6. Mock 通道：下单即视为成功
        if (providerResult.immediatelyPaid()) {
            markPaid(orderId, providerResult.prepayId(), null);
        }

        return new CreateOrderResponse(
                orderId,
                channel,
                amountCents,
                currency,
                providerResult.clientPayload(),
                providerResult.immediatelyPaid()
        );
    }

    // ====== 标记支付成功（回调 / Mock 都用） ======

    /**
     * 标记订单已支付，并发布 PaymentSucceededEvent 触发会员激活。
     *
     * <p><b>幂等</b>：同一 orderId 重复调用直接 return 不再发事件，防止：
     * <ul>
     *   <li>微信回调网络抖动重试；</li>
     *   <li>定时任务的补偿查询又触发一次。</li>
     * </ul>
     *
     * @param orderId               业务订单号
     * @param channelPrepayId       通道预支付号（微信 prepay_id），可空
     * @param channelTransactionId  通道凭证号（微信 transaction_id / Apple transaction_id），可空
     */
    @Transactional
    public void markPaid(String orderId, String channelPrepayId, String channelTransactionId) {
        markPaid(orderId, channelPrepayId, channelTransactionId, null);
    }

    /**
     * 带金额校验的 markPaid 重载 — 用于真实支付通道回调（如微信、支付宝）。
     *
     * <p><b>金额校验</b>：当 {@code paidAmountCents != null} 时，会校验回调金额与订单金额一致；
     * 不一致直接抛 {@link ResultCode#PAYMENT_ORDER_AMOUNT_MISMATCH} 并打 ERROR 日志，
     * 订单状态保持 PENDING（让微信重试或人工核查），<b>避免少付钱被静默放行的资损风险</b>。
     *
     * <p>Mock provider / 测试场景可继续用单参版本（透传 null 跳过校验）。
     *
     * @param orderId              业务订单号
     * @param channelPrepayId      通道预支付号（微信 prepay_id），可空
     * @param channelTransactionId 通道凭证号（微信 transaction_id / Apple transaction_id），可空
     * @param paidAmountCents      通道回传的实付金额（单位：分）；null 表示跳过金额校验
     */
    @Transactional
    public void markPaid(String orderId, String channelPrepayId, String channelTransactionId,
                          Long paidAmountCents) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.PAID) {
            log.info("[payment] 订单已支付，幂等返回 orderId={}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ResultCode.PAYMENT_ORDER_STATUS_INVALID,
                    "订单状态不允许标记为已支付：" + order.getStatus());
        }

        // 金额校验（仅当通道回传金额时才校验，Mock 场景透传 null 跳过）
        if (paidAmountCents != null && !paidAmountCents.equals(order.getAmountCents())) {
            log.error("[payment] ⚠️ 订单金额校验失败！orderId={} expectedCents={} paidCents={} channel={}" +
                            " — 拒绝标记支付，订单保持 PENDING 等待人工核查",
                    orderId, order.getAmountCents(), paidAmountCents, order.getChannel());
            throw new BusinessException(ResultCode.PAYMENT_ORDER_AMOUNT_MISMATCH);
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        if (channelPrepayId != null) order.setChannelPrepayId(channelPrepayId);
        if (channelTransactionId != null) order.setChannelTransactionId(channelTransactionId);
        orderRepository.save(order);

        // 发布事件 — 会员模块的 @TransactionalEventListener(AFTER_COMMIT) 会在事务提交后激活
        eventPublisher.publishEvent(PaymentSucceededEvent.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .planCode(order.getPlanCode())
                .channel(order.getChannel().name())
                .amountCents(order.getAmountCents())
                .currency(order.getCurrency())
                .paidAt(order.getPaidAt())
                .build());

        log.info("[payment] 订单标记已支付 orderId={} userId={} channel={} amountCents={} 已发布事件",
                orderId, order.getUserId(), order.getChannel(), order.getAmountCents());
    }

    /**
     * 关闭订单（用户取消 / 超时未付）
     */
    @Transactional
    public void closeOrder(String orderId, String reason) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.PENDING) {
            // 已支付/已关闭都直接返回，幂等
            return;
        }
        order.setStatus(OrderStatus.CLOSED);
        order.setClosedAt(LocalDateTime.now());
        order.setFailReason(reason);
        orderRepository.save(order);
        log.info("[payment] 关闭订单 orderId={} reason={}", orderId, reason);
    }

    /**
     * Admin 标记退款 — V1 实现：**仅记账，不实际调用通道退款接口**。
     *
     * <p>这样设计的原因：微信商户号未到位，Apple 退款由用户在 Apple 系统直接发起 server 收 notification。
     * V1 阶段 admin 在后台标记退款用于会计 + 关停会员；真正的「钱原路退回」走线下流程（财务手工微信转账 / 苹果系统）。
     *
     * <p>后续接入真正的退款接口时，在此处增加：
     * <ul>
     *   <li>调 WeChatPaymentProvider.refund(channelTransactionId, amount)；</li>
     *   <li>refundStatus 由 PENDING → COMPLETED / FAILED 走真实异步状态。</li>
     * </ul>
     *
     * @param orderId       业务订单号
     * @param refundCents   退款金额（最小货币单位），<=0 时按全额退
     * @param reason        退款原因（必填，落 fail_reason 便于审计）
     */
    @Transactional
    public PaymentOrder adminMarkRefunded(String orderId, Integer refundCents, String reason) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ResultCode.PAYMENT_ORDER_STATUS_INVALID,
                    "只允许对 PAID 订单标记退款，当前状态：" + order.getStatus());
        }
        int amount = (refundCents == null || refundCents <= 0)
                ? order.getAmountCents()
                : Math.min(refundCents, order.getAmountCents());

        order.setStatus(OrderStatus.REFUNDED);
        order.setRefundStatus(com.lanprojects.fitcoach.payment.entity.RefundStatus.COMPLETED);
        order.setRefundAmountCents(amount);
        order.setRefundedAt(LocalDateTime.now());
        order.setFailReason(reason);
        orderRepository.save(order);
        log.info("[payment] Admin 标记退款 orderId={} amount={} reason={}", orderId, amount, reason);
        return order;
    }

    // ====== 查询 ======

    public Optional<PaymentOrder> findByOrderId(String orderId) {
        return orderRepository.findByOrderId(orderId);
    }

    public PaymentOrder requireOrderForUser(String orderId, Long userId) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ResultCode.PAYMENT_ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PAYMENT_ORDER_NOT_OWNED);
        }
        return order;
    }

    public Page<PaymentOrder> listMyOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<PaymentOrder> adminList(Pageable pageable) {
        return orderRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<PaymentOrder> adminListByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    // ====== 内部 ======

    /** 业务订单号生成器：时间戳 + userId 后 4 位 + 4 位随机数。 */
    private String generateOrderId(Long userId) {
        String suffix = String.format("%04d",
                userId == null ? 0 : (Math.abs(userId.intValue()) % 10000));
        String rand = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return LocalDateTime.now().format(ORDER_ID_FORMAT) + suffix + rand;
    }

    /**
     * 微信 attach 字段透传 JSON：本系统将 userId / planCode 编进去，回调时再解。
     *
     * <p>必须用 ObjectMapper 序列化，不能字符串拼接 —— planCode 虽然来自 server 校验过的 plan，
     * 但若运营后台允许配置含特殊字符（引号 / 反斜线 / 换行）的 planCode，
     * 拼接会破坏 JSON 结构甚至构成注入。LinkedHashMap 保证字段顺序稳定，
     * 便于回调日志比对。
     */
    private String buildAttachJson(Long userId, String planCode) {
        Map<String, Object> attach = new LinkedHashMap<>(2);
        attach.put("userId", userId);
        attach.put("planCode", planCode);
        try {
            return objectMapper.writeValueAsString(attach);
        } catch (JsonProcessingException e) {
            // 防御性 fallback：理论上 LinkedHashMap<String,Object> 永远不会序列化失败；
            // 真出问题就退化成 userId-only attach，避免阻塞下单主链路。
            log.error("[payment] 序列化 attach 失败 userId={} planCode={} ", userId, planCode, e);
            return "{\"userId\":" + userId + "}";
        }
    }

    // ====== 出参 ======

    /**
     * 下单结果（Controller → 客户端）。
     *
     * @param orderId           业务订单号
     * @param channel           实际走的通道
     * @param amountCents       实付金额（最小货币单位）
     * @param currency          币种
     * @param clientPayload     客户端拉起支付所需的 SDK 参数
     * @param immediatelyPaid   该订单已经直接被标记为支付成功（Mock 通道场景，客户端无需拉起 SDK）
     */
    public record CreateOrderResponse(
            String orderId,
            PaymentChannel channel,
            int amountCents,
            String currency,
            Map<String, Object> clientPayload,
            boolean immediatelyPaid
    ) {
        /** 兜底空 payload */
        public static CreateOrderResponse empty(String orderId, PaymentChannel channel) {
            return new CreateOrderResponse(orderId, channel, 0, CURRENCY_CNY, new HashMap<>(), false);
        }
    }
}
