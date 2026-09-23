package com.lanprojects.fitcoach.payment.provider.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.payment.provider.PaymentConfigKeys;
import com.lanprojects.fitcoach.payment.service.*;
import com.lanprojects.fitcoach.payment.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class WeChatPublicKeyTest {
    SysConfigService config;
    WeChatSignatureVerifier verifier;
    KeyPair pair;
    final String id = "PUB_KEY_ID_test";
    final String apiKey = "0123456789abcdef0123456789abcdef";
    final ObjectMapper json = new ObjectMapper();
    @BeforeEach void init() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); pair = generator.generateKeyPair();
        config = mock(SysConfigService.class);
        when(config.getValue(PaymentConfigKeys.WECHAT_PUBLIC_KEY_ID)).thenReturn(id);
        when(config.getValue(PaymentConfigKeys.WECHAT_PUBLIC_KEY_PEM)).thenReturn("-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
        when(config.getValue(PaymentConfigKeys.WECHAT_API_V3_KEY)).thenReturn(apiKey);
        when(config.getValue(PaymentConfigKeys.WECHAT_APP_ID)).thenReturn("app");
        when(config.getValue(PaymentConfigKeys.WECHAT_MCH_ID)).thenReturn("merchant");
        verifier = new WeChatSignatureVerifier(config);
    }
    String sign(String time, String body) { return WeChatPayV3Helper.sign(time+"\nnonce\n"+body+"\n", pair.getPrivate()); }
    @Test void exactPublicKeyIdAuthenticatesBodyAndRejectsTamperingAndStaleTimestamp() {
        String time = Long.toString(System.currentTimeMillis()/1000); String body = "{\"prepay_id\":\"test\"}";
        assertTrue(verifier.isConfigured()); assertEquals(id, verifier.preferredSerial());
        assertTrue(verifier.verify(time,"nonce",body,sign(time,body),id));
        assertTrue(verifier.verify(time,"nonce","",sign(time,""),id));
        assertFalse(verifier.verify(time,"nonce",body+" ",sign(time,body),id));
        assertFalse(verifier.verify(time,"nonce",body,sign(time,body),"PUB_KEY_ID_other"));
        assertFalse(verifier.verify(time,"nonce",body,"WECHATPAY/SIGNTEST/invalid",id));
        String old=Long.toString(System.currentTimeMillis()/1000-301);
        assertFalse(verifier.verify(old,"nonce",body,sign(old,body),id));
        assertFalse(verifier.verify(Long.toString(Long.MIN_VALUE),"nonce",body,"bad",id));
    }
    @Test void incompletePairCannotEnablePayment() {
        when(config.getValue(PaymentConfigKeys.WECHAT_PUBLIC_KEY_PEM)).thenReturn("");
        assertFalse(verifier.isConfigured());
        assertThrows(IllegalArgumentException.class, verifier::preferredSerial);
    }
    Map<String,Object> transaction() {
        return new HashMap<>(Map.of("appid","app","mchid","merchant","trade_state","SUCCESS",
                "transaction_id","tx","out_trade_no","order","amount",Map.of("total",100,"currency","CNY")));
    }
    String notification(Map<String,Object> transaction) throws Exception {
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");String nonce="123456789012";
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8),"AES"),
                new GCMParameterSpec(128,nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD("transaction".getBytes(StandardCharsets.UTF_8));
        return json.writeValueAsString(Map.of("event_type","TRANSACTION.SUCCESS","resource",
                Map.of("algorithm","AEAD_AES_256_GCM","nonce",nonce,"associated_data","transaction","ciphertext",
                        Base64.getEncoder().encodeToString(cipher.doFinal(json.writeValueAsBytes(transaction))))));
    }
    @Test void signedEncryptedNotificationValidatesMerchantAmountAndChannelBeforeGranting() throws Exception {
        var payments=mock(PaymentService.class);var refunds=mock(RefundService.class);
        var order=new PaymentOrder();order.setChannel(PaymentChannel.WECHAT);order.setAmountCents(100);
        when(payments.findByOrderId("order")).thenReturn(Optional.of(order));
        var handler=new WeChatCallbackHandler(config,payments,refunds,json,verifier);
        String time=Long.toString(System.currentTimeMillis()/1000);var tx=transaction();String body=notification(tx);
        assertTrue(handler.handleCallback(time,"nonce",sign(time,body),id,body));
        verify(payments).markPaid("order",null,"tx",100L);
        clearInvocations(payments);
        tx.put("appid","other");body=notification(tx);
        assertFalse(handler.handleCallback(time,"nonce",sign(time,body),id,body));
        tx=transaction();tx.put("amount",Map.of("total",99,"currency","CNY"));body=notification(tx);
        assertFalse(handler.handleCallback(time,"nonce",sign(time,body),id,body));
        order.setStatus(OrderStatus.REFUNDED);order.setChannelTransactionId("tx");body=notification(transaction());
        assertTrue(handler.handleCallback(time,"nonce",sign(time,body),id,body));
        order.setChannel(PaymentChannel.MOCK);body=notification(transaction());
        assertFalse(handler.handleCallback(time,"nonce",sign(time,body),id,body));
        verify(payments,never()).markPaid(anyString(),any(),any(),any());
    }
    @Test void legacySkipFlagDoesNotBypassSignature() throws Exception {
        when(config.getBoolValue(PaymentConfigKeys.WECHAT_SKIP_CALLBACK_SIGNATURE,false)).thenReturn(true);
        var payments=mock(PaymentService.class);
        var handler=new WeChatCallbackHandler(config,payments,mock(RefundService.class),json,verifier);
        assertFalse(handler.handleCallback("0","nonce","bad",id,notification(transaction())));
        verifyNoInteractions(payments);
    }
}
