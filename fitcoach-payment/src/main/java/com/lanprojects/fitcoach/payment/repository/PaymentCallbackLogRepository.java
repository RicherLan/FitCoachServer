package com.lanprojects.fitcoach.payment.repository;

import com.lanprojects.fitcoach.payment.entity.PaymentCallbackLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentCallbackLogRepository extends JpaRepository<PaymentCallbackLog, Long> {

    /** 排查问题用：按订单号查所有回调记录（按时间倒序） */
    List<PaymentCallbackLog> findByOrderIdOrderByCreatedAtDesc(String orderId);
}
