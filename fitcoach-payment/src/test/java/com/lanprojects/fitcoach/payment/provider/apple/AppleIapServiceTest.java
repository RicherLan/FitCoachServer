package com.lanprojects.fitcoach.payment.provider.apple;

import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.payment.entity.*;
import com.lanprojects.fitcoach.payment.repository.PaymentOrderRepository;
import com.lanprojects.fitcoach.payment.service.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppleIapServiceTest {
    @Test void rejectsOtherUsersAndMismatchedProductBeforeGranting() {
        var config = mock(SysConfigService.class);
        when(config.getBoolValue(anyString(), anyBoolean())).thenReturn(true);
        var orders = mock(PaymentOrderRepository.class);
        var payment = mock(PaymentService.class);
        var verifier = mock(AppleSignedDataVerifier.class);
        var service = new AppleIapService(config, payment, mock(RefundService.class), orders, verifier);
        var order = new PaymentOrder(); order.setOrderId("one"); order.setUserId(1L);
        order.setChannel(PaymentChannel.APPLE_IAP); order.setExtraJson("{\"productId\":\"year\"}");
        when(orders.findLockedByOrderId("one")).thenReturn(Optional.of(order));
        assertThrows(BusinessException.class, () -> service.verifyAndComplete(2L, "one", "jws"));
        verifyNoInteractions(verifier);
        var tx = new JWSTransactionDecodedPayload();
        tx.setTransactionId("tx"); tx.setProductId("month"); tx.setPurchaseDate(1L);
        tx.setAppAccountToken(AppleSignedDataVerifier.accountToken("one"));
        when(verifier.transaction("jws")).thenReturn(tx);
        assertThrows(BusinessException.class, () -> service.verifyAndComplete(1L, "one", "jws"));
        verifyNoInteractions(payment);
        tx.setProductId("year");
        service.verifyAndComplete(1L, "one", "jws");
        verify(payment).markPaid("one", null, "tx");
        tx.setAppAccountToken(AppleSignedDataVerifier.accountToken("another-order"));
        assertThrows(BusinessException.class, () -> service.verifyAndComplete(1L, "one", "jws"));
        tx.setAppAccountToken(AppleSignedDataVerifier.accountToken("one")); tx.setRevocationDate(2L);
        assertThrows(BusinessException.class, () -> service.verifyAndComplete(1L, "one", "jws"));
    }
    @Test void missingTrustedRootsNeverDecodesUnverifiedPayload() {
        var verifier = new AppleSignedDataVerifier(mock(SysConfigService.class));
        assertThrows(BusinessException.class, () -> verifier.transaction("e30.e30.signature"));
        assertThrows(BusinessException.class, () -> verifier.notification("e30.e30.signature"));
    }
}
