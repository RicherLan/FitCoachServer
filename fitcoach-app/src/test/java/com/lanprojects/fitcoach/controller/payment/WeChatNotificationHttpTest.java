package com.lanprojects.fitcoach.controller.payment;

import com.lanprojects.fitcoach.payment.provider.wechat.WeChatCallbackHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WeChatNotificationHttpTest {
    @Test void paymentAcknowledgesOnlySuccessfulProcessing() throws Exception {
        var handler = mock(WeChatCallbackHandler.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PaymentController(null, null, handler, null, null)).build();
        when(handler.handleCallback(null, null, null, null, "{}" )).thenReturn(false, true);
        mvc.perform(post("/api/payment/notify/wechat").contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("FAIL"));
        mvc.perform(post("/api/payment/notify/wechat").contentType("application/json").content("{}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }
    @Test void refundFailureRequestsWechatRetry() throws Exception {
        var handler = mock(WeChatCallbackHandler.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PaymentController(null, null, handler, null, null)).build();
        when(handler.handleRefundCallback(null, null, null, null, "{}" )).thenReturn(false, true);
        mvc.perform(post("/api/payment/notify/wechat/refund").contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/payment/notify/wechat/refund").contentType("application/json").content("{}"))
                .andExpect(status().isNoContent());
    }
}
