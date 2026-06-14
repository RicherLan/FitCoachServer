package com.lanprojects.fitcoach.notification.service;

import com.lanprojects.fitcoach.common.clientbus.ClientPollContribution;
import com.lanprojects.fitcoach.notification.dto.SystemNotificationClientDto;
import com.lanprojects.fitcoach.notification.entity.SystemNotificationEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统通知（站内弹窗）对客户端通用轮询通道（{@code GET /api/client/poll}）的贡献。
 *
 * <p>响应中以 {@code "systemNotification"} 字段输出：
 * <ul>
 *   <li>命中（List 非空） → {@code List<SystemNotificationClientDto>}（按 createdAt DESC，最早的弹完弹下一条）</li>
 *   <li>未命中（List 为空） → 字段缺失（contribution 返回 null 时，controller 不会写入响应；节省客户端解析开销）</li>
 * </ul>
 *
 * <p><b>v1.1 改动</b>：从"单条"改为"List"，原因见
 * {@link SystemNotificationService#findAllForUser(String)} 的 javadoc；客户端 systemNotificationHandler
 * 收到 List 后按队列依次弹出（当前弹窗关闭再弹下一条），避免多条通知互相覆盖。
 *
 * <p>"是否已看过"判定仍全部交给客户端 —— 服务端永远返回 uid 当前所有可见的通知；
 * 客户端在 storage 里维护 {@code seenIds: Set<number>}，命中即跳过；用户卸载重装会清空，
 * 用户已同意此行为（reinstall 重看一次 OK）。
 *
 * <p>本 Bean 只是 SPI 适配层；过滤逻辑（时间窗口 + 投放对象 + 平台 + 版本）全部在
 * {@link SystemNotificationService#findAllForUser(String)} 里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemNotificationPollContribution implements ClientPollContribution {

    /** 客户端 JSON 字段名 —— 客户端 systemNotificationHandler 据此读取 */
    public static final String KEY = "systemNotification";

    private final SystemNotificationService notificationService;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Object resolve(String uid) {
        List<SystemNotificationEntity> all = notificationService.findAllForUser(uid);
        if (all.isEmpty()) {
            // 返回 null 让 controller 跳过此字段，省去客户端解析空数组的开销
            return null;
        }
        return all.stream().map(SystemNotificationClientDto::from).toList();
    }
}
