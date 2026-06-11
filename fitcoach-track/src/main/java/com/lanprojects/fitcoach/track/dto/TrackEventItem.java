package com.lanprojects.fitcoach.track.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 单条埋点事件（客户端 → server 上报体）。
 *
 * <p><b>字段来源约定</b>：
 * <ul>
 *   <li>身份相关（deviceId / platform / appVersion / bundleVersion / locale） — 从 HTTP Header 取，
 *       <b>不在本 DTO 中重复</b>，防止客户端伪造；</li>
 *   <li>userId — 从 Authorization token 解析，未登录则 null；</li>
 *   <li>本 DTO 仅承载"事件本身的属性 + 客户端补充的少量元数据"。</li>
 * </ul>
 */
@Data
@Schema(description = "单条埋点事件上报体")
public class TrackEventItem {

    @Schema(description = "事件 key（蛇形小写，如 home_click_settings）",
            example = "home_click_settings", requiredMode = Schema.RequiredMode.REQUIRED)
    private String eventKey;

    @Schema(description = "会话标识（一次冷启动一个 UUID）",
            example = "5e7b9c4f-8a2d-4c91-b3e5-7a8b9c0d1e2f", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;

    @Schema(description = "客户端事件发生毫秒时间戳（UTC）", example = "1704067200000",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long clientTs;

    @Schema(description = "操作系统版本（client 补充，header 没有）", example = "Android 14")
    private String osVersion;

    @Schema(description = "设备时区", example = "Asia/Shanghai")
    private String timezone;

    @Schema(description = "客户端粗略 region（兜底，server 会用 GeoIP 覆盖）", example = "CN")
    private String region;

    @Schema(description = "网络类型（预留）", example = "wifi",
            allowableValues = {"wifi", "4g", "5g", "unknown"})
    private String networkType;

    @Schema(description = "业务自定义属性（扁平 KV，避免嵌套）",
            example = "{\"exerciseKey\":\"SQUAT\",\"source\":\"tab\"}")
    private Map<String, String> properties;
}
