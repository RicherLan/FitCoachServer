package com.lanprojects.fitcoach.track.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 埋点事件响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackEventResponse {
    /**
     * 事件 ID
     */
    private Long id;

    /**
     * 事件 Key
     */
    private String eventKey;

    /**
     * 用户 ID（可空）
     */
    private String userId;

    /**
     * 设备 ID
     */
    private String deviceId;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 平台（android / ios）
     */
    private String platform;

    /**
     * 应用版本
     */
    private String appVersion;

    /**
     * Bundle 版本
     */
    private String bundleVersion;

    /**
     * 操作系统版本
     */
    private String osVersion;

    /**
     * 语言（BCP-47）
     */
    private String locale;

    /**
     * 地区码
     */
    private String region;

    /**
     * 时区
     */
    private String timezone;

    /**
     * 网络类型
     */
    private String networkType;

    /**
     * 客户端时刻（毫秒）
     */
    private Long clientTs;

    /**
     * 服务端时刻（毫秒）
     */
    private Long serverTs;

    /**
     * 业务属性
     */
    private Map<String, String> properties;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
