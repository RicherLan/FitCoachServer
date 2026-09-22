package com.lanprojects.fitcoach.payment.service;

import com.lanprojects.fitcoach.payment.entity.*;
import com.lanprojects.fitcoach.payment.repository.PaymentOrderRepository;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    @Test void acceptsSameAmountAcrossIntegerAndLongAndRejectsMismatch() {
        var repo = mock(PaymentOrderRepository.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var service = new PaymentService(repo, mock(PaymentChannelRouter.class), publisher);
        var order = new PaymentOrder();
        order.setOrderId("order"); order.setAmountCents(9900); order.setChannel(PaymentChannel.WECHAT);
        when(repo.findLockedByOrderId("order")).thenReturn(Optional.of(order));
        assertThrows(BusinessException.class, () -> service.markPaid("order", null, "txn", 9901L));
        assertEquals(OrderStatus.PENDING, order.getStatus());
        service.markPaid("order", null, "txn", 9900L);
        assertEquals(OrderStatus.PAID, order.getStatus());
        service.markPaid("order", null, "txn", 9900L);
        service.closeOrder("order", "expired job observed an old pending snapshot");
        assertEquals(OrderStatus.PAID, order.getStatus());
        verify(repo, times(4)).findLockedByOrderId("order");
        verify(publisher, times(1)).publishEvent(any(Object.class));
    }
}
