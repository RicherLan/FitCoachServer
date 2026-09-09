package com.lanprojects.fitcoach.common.event;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 支付成功事件 — 由 fitcoach-payment 模块在订单状态从 PENDING 切换到 PAID 后**事务提交后**发布。
 *
 * <p><b>放在 fitcoach-common 而不是 fitcoach-payment</b> 的理由：
 * <ul>
 *   <li>事件本质是跨域契约（消息）。把契约下沉到 common，让 payment 和业务模块之间不产生编译期依赖；</li>
 *   <li>未来其他业务模块（积分、签到、营销）想监听支付成功也只需依赖 common；</li>
 *   <li>事件类不应携带任何 payment 模块的内部实现细节（不要传 PaymentOrder 实体本身），
 *       只传业务语义所需的最小必要字段——这是「贫血事件 + 后置查询」模式。</li>
 * </ul>
 *
 * <p><b>去业务化（波 0）</b>：事件用通用的 {@code productType + productCode} 描述"买了什么"，
 * payment 模块不认识具体业务。消费方按 {@code productType} 分发：
 * <pre>
 *   if ("MEMBERSHIP".equals(event.getProductType())) {
 *       membershipService.activate(userId, event.getProductCode() 作为 planCode, orderId);
 *   }
 *   // 其他 App 卖金币：if ("COINS".equals(productType)) coinsService.grant(...);
 * </pre>
 *
 * <p><b>消费方约束</b>：
 * <ul>
 *   <li>监听器必须是幂等的（同一 orderId 可能被重复处理：补偿任务、人工触发等）；</li>
 *   <li>监听器不应抛异常打断主事务——payment 模块用 {@code @TransactionalEventListener(AFTER_COMMIT)}
 *       保证事件在事务提交后才发出，监听器内部的异常不会回滚 payment 事务，但要自己 try/catch 记日志。</li>
 * </ul>
 *
 * @param orderId     业务订单号（UUID 字符串），可作为幂等 key
 * @param userId      购买用户 id
 * @param productType 商品类型（业务方自定义，如 "MEMBERSHIP"），消费方据此决定是否处理该事件
 * @param productCode 商品业务 code（如会员套餐 "MONTHLY"），消费方据此查询商品详情并发货
 * @param channel     支付通道（WECHAT / APPLE_IAP / GOOGLE_PLAY / ...），用于统计/审计
 * @param amountCents 实付金额（最小货币单位：分 / 美分），用于审计
 * @param currency    货币（CNY / USD），用于审计
 * @param paidAt      支付完成时间
 */
@Getter
@ToString
@Builder
public class PaymentSucceededEvent {

    private final String orderId;
    private final Long userId;
    private final String productType;
    private final String productCode;
    private final String channel;
    private final int amountCents;
    private final String currency;
    private final LocalDateTime paidAt;
}
