package com.lanprojects.fitcoach.payment.service;

import com.lanprojects.fitcoach.payment.entity.*;
import com.lanprojects.fitcoach.payment.provider.*;
import com.lanprojects.fitcoach.payment.repository.*;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundServiceTest {
    final PaymentOrderRepository orders = mock(PaymentOrderRepository.class);
    final RefundOrderRepository refunds = mock(RefundOrderRepository.class);
    final PaymentChannelProvider provider = mock(PaymentChannelProvider.class);
    final PaymentChannelRouter router = mock(PaymentChannelRouter.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final List<RefundOrder> rows = new ArrayList<>();
    final PaymentOrder order = new PaymentOrder();
    final RefundService service;

    RefundServiceTest() {
        order.setOrderId("order"); order.setUserId(1L); order.setStatus(OrderStatus.PAID);
        order.setChannel(PaymentChannel.WECHAT); order.setAmountCents(100); order.setCurrency("CNY");
        when(orders.findLockedByOrderId("order")).thenReturn(Optional.of(order));
        when(router.require(PaymentChannel.WECHAT)).thenReturn(provider);
        when(provider.supportsActiveRefund()).thenReturn(true);
        when(refunds.findByOrderIdOrderByCreatedAtDesc("order")).thenAnswer(i -> rows);
        when(refunds.findByRefundNo(anyString())).thenAnswer(i -> rows.stream().filter(r -> r.getRefundNo().equals(i.getArgument(0))).findFirst());
        when(refunds.saveAndFlush(any())).thenAnswer(i -> { RefundOrder r = i.getArgument(0); if (!rows.contains(r)) { rows.add(r); } return r; });
        when(refunds.sumAmountByOrderIdAndStatus(anyString(), any())).thenAnswer(i -> rows.stream()
                .filter(r -> r.getStatus() == i.getArgument(1)).mapToLong(RefundOrder::getAmountCents).sum());
        var manager = new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object t, TransactionDefinition d) {}
            protected void doCommit(DefaultTransactionStatus s) {}
            protected void doRollback(DefaultTransactionStatus s) {}
        };
        service = new RefundService(orders, refunds, router, events, manager);
    }
    @Test void externalCallIsOutsideTransactionAndFailureRemainsRecorded() {
        when(provider.refund(any())).thenAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, rows.size());
            return new RefundResult(null, false, false, "rejected");
        });
        assertThrows(BusinessException.class, () -> service.refund("order", 100, "reason", "admin"));
        assertEquals(RefundStatus.FAILED, rows.get(0).getStatus());
    }
    @Test void timeoutKeepsReservationAndRetryUsesSameRefundNumber() {
        when(provider.refund(any())).thenThrow(new IllegalStateException("timeout"));
        assertThrows(BusinessException.class, () -> service.refund("order", 100, "reason", "admin"));
        String number = rows.get(0).getRefundNo();
        assertEquals(RefundStatus.PENDING, rows.get(0).getStatus());
        assertEquals(number, service.refund("order", 100, "again", "admin").getRefundNo());
        verify(provider, times(1)).refund(any());
        doReturn(new RefundResult("channel", true, true, "success")).when(provider).refund(any());
        service.retryPending("order", number);
        assertEquals(OrderStatus.REFUNDED, order.getStatus());
        assertEquals(1, rows.size());
    }
    @Test void earlyCallbackWinsAndDuplicatesDoNotPublishTwice() {
        when(provider.refund(any())).thenAnswer(i -> {
            RefundRequest req = i.getArgument(0);
            service.completeByRefundNo(req.refundNo(), "channel", "order", 100, "callback");
            return new RefundResult("channel", true, false, "processing");
        });
        var r = service.refund("order", 100, "reason", "admin");
        assertEquals(RefundStatus.COMPLETED, r.getStatus());
        service.completeByRefundNo(r.getRefundNo(), "channel", "order", 100, "again");
        verify(events, times(1)).publishEvent(any(Object.class));
        assertThrows(BusinessException.class, () -> service.completeByRefundNo(r.getRefundNo(), "channel", "order", 99, "wrong"));
    }
}
