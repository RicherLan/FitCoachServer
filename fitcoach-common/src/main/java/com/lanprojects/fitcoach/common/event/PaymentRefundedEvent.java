package com.lanprojects.fitcoach.common.event;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 退款成功事件 —— 由 payment 模块在退款单状态切到 {@code COMPLETED} 后**事务提交后**发布。
 *
 * <p>与 {@link PaymentSucceededEvent} 完全对称的「贫血事件 + 后置查询」设计：
 * <ul>
 *   <li>放在 fitcoach-common，让 payment 与业务模块无编译期依赖；</li>
 *   <li>用 {@code productType + productCode} 描述退的是什么商品，payment 不认识业务；</li>
 *   <li>业务模块按 {@code productType} 认领后撤销 / 扣减权益（会员按此撤销或按比例扣天数）。</li>
 * </ul>
 *
 * <p><b>消费方约束</b>：监听器必须幂等（同一 refundNo 可能因通知重投被重复处理），
 * 且不应抛异常打断主事务（payment 用 {@code AFTER_COMMIT} 发布，监听器自行 try/catch）。
 *
 * @param orderId       原支付订单号（业务号）
 * @param refundNo      退款单号（可作幂等 key）
 * @param userId        退款用户 id
 * @param productType   商品类型（业务方据此决定是否认领，如 "MEMBERSHIP"）
 * @param productCode   商品业务 code（会员语境即 planCode）
 * @param refundCents   本次退款金额（最小货币单位：分 / 美分）
 * @param currency      币种（CNY / USD）
 * @param fullyRefunded 是否已全额退款：true=订单累计退款已达原额，业务方应<b>完全撤销</b>权益；
 *                      false=部分退款，业务方按自身策略处理（如会员按比例扣减天数或不处理）
 * @param refundedAt    退款完成时间
 */
@Getter
@ToString
@Builder
public class PaymentRefundedEvent {

    private final String orderId;
    private final String refundNo;
    private final Long userId;
    private final String productType;
    private final String productCode;
    private final int refundCents;
    private final String currency;
    private final boolean fullyRefunded;
    private final LocalDateTime refundedAt;
}
