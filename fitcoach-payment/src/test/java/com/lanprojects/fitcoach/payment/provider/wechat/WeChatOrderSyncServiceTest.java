package com.lanprojects.fitcoach.payment.provider.wechat;
import com.lanprojects.fitcoach.payment.entity.*;
import com.lanprojects.fitcoach.payment.repository.PaymentOrderRepository;
import com.lanprojects.fitcoach.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class WeChatOrderSyncServiceTest {
    @Test void paidRemoteOrderIsReconciledInsteadOfCancelled() {
        var provider=mock(WeChatPaymentProvider.class);var handler=mock(WeChatCallbackHandler.class);var payments=mock(PaymentService.class);
        var service=new WeChatOrderSyncService(provider,handler,payments,mock(PaymentOrderRepository.class));
        var order=new PaymentOrder();order.setOrderId("order");order.setChannel(PaymentChannel.WECHAT);
        when(payments.findByOrderId("order")).thenReturn(Optional.of(order));
        var result=Map.<String,Object>of("trade_state","SUCCESS");when(provider.queryTransaction("order")).thenReturn(result);
        when(handler.applyVerifiedTransaction(result)).thenReturn(true);
        service.closeOrder("order","cancel");
        verify(handler).applyVerifiedTransaction(result);verify(provider,never()).closeRemoteOrder(any());verify(payments,never()).closeOrder(any(),any());
    }
    @Test void remoteCloseFailureLeavesLocalOrderPendingAndSuccessfulCloseOrdersOperations() {
        var provider=mock(WeChatPaymentProvider.class);var handler=mock(WeChatCallbackHandler.class);var payments=mock(PaymentService.class);
        var service=new WeChatOrderSyncService(provider,handler,payments,mock(PaymentOrderRepository.class));
        var order=new PaymentOrder();order.setOrderId("order");order.setChannel(PaymentChannel.WECHAT);
        when(payments.findByOrderId("order")).thenReturn(Optional.of(order));
        when(provider.queryTransaction("order")).thenReturn(Map.of("trade_state","NOTPAY"));
        doThrow(new IllegalStateException()).when(provider).closeRemoteOrder("order");
        assertThrows(IllegalStateException.class,()->service.closeOrder("order","cancel"));verify(payments,never()).closeOrder(any(),any());
        doNothing().when(provider).closeRemoteOrder("order");clearInvocations(provider,payments);
        service.closeOrder("order","cancel");var sequence=inOrder(provider,payments);
        sequence.verify(provider).closeRemoteOrder("order");sequence.verify(payments).closeOrder("order","cancel");
    }
}
