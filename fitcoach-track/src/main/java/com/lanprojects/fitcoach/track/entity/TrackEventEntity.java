package com.lanprojects.fitcoach.track.entity;

import com.lanprojects.fitcoach.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端产品埋点事件（APM Phase 1）。
 *
 * <p><b>设计理念：三层结构</b>
 * <ul>
 *   <li><b>Key</b>（{@link #eventKey}）：事件唯一标识，蛇形小写，由客户端常量集中维护；</li>
 *   <li><b>基础信息</b>：身份 / 设备 / 时间 / 地区 等所有事件都有的元数据；</li>
 *   <li><b>业务信息</b>（{@link #properties}）：完全自由的 KV，存进 JSON 列。</li>
 * </ul>
 *
 * <p><b>关键决策说明：</b>
 * <ul>
 *   <li><b>server_ts 是权威时间</b> — 所有时间维度索引/查询都基于 {@link #serverTs}，
 *       不基于客户端 {@link #clientTs}（设备时间可被用户修改、跨时区有偏差）；
 *       {@link BaseEntity#getCreatedAt()} 仍然由 JPA 自动填，但内部索引/分析走 long 形式更直接。</li>
 *   <li><b>userId 可空</b> — 未登录用户也允许上报（注册前的浏览行为对漏斗分析很有价值），
 *       上报接口对 token 做 try-best 解析，没有就 null。</li>
 *   <li><b>region 由 server 端 GeoIP 覆盖</b>（V2 实现）— 客户端 locale/country 兜底，
 *       服务端拿到真实出口 IP 后用 MaxMind GeoLite2 解析覆盖更准的值，海外商店上线后做按区域切分用。</li>
 *   <li><b>properties 用 JSON 列 + AttributeConverter</b> — 与 feedback 的
 *       {@code JsonStringListConverter} 模式一致；扁平 KV 字符串值即可，
 *       不嵌套 object/array 以便后续 admin 端做 {@code JSON_EXTRACT} 查询。</li>
 *   <li><b>索引覆盖三类主查询</b>：按事件名 + 按用户 + 按设备 + 按 session（漏斗）+ 按地区（海外切分）。</li>
 * </ul>
 *
 * <p><b>分区/归档规划</b>：当前 ddl-auto=update 不会建分区表；待数据量增长后由 DBA
 * 在线下手动改为 RANGE PARTITION BY server_ts 月度分区，老数据按月 DETACH 归档冷存储。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "track_event", indexes = {
        @Index(name = "idx_event_ts",   columnList = "event_key, server_ts"),
        @Index(name = "idx_user_ts",    columnList = "user_id, server_ts"),
        @Index(name = "idx_device_ts",  columnList = "device_id, server_ts"),
        @Index(name = "idx_session",    columnList = "session_id"),
        @Index(name = "idx_region_ts",  columnList = "region, server_ts")
})
public class TrackEventEntity extends BaseEntity {

    /** 事件 key（蛇形小写，如 home_click_settings），由客户端 TrackEvent 常量集中维护 */
    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;

    // ====== 身份 ======

    /** 用户 uid（来自 User.uid，未登录为 null；不做外键避免跨模块强耦合） */
    @Column(name = "user_id", length = 64)
    private String userId;

    /** 设备唯一标识（RN 端 UUIDv4 首次安装持久化；来自 X-Device-Id header） */
    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    /** 会话标识（一次冷启动一个 UUID；用于行为序列/漏斗分析） */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    // ====== 设备 ======

    /** 客户端平台：android / ios（来自 X-Client-Platform header） */
    @Column(name = "platform", nullable = false, length = 16)
    private String platform;

    /** 应用市场版本（X-Client-Native-Version-Name，如 "1.2.3"） */
    @Column(name = "app_version", length = 32)
    private String appVersion;

    /** JS bundle 版本（X-Client-Bundle-Version-Name；OTA 后会与 native 不同） */
    @Column(name = "bundle_version", length = 32)
    private String bundleVersion;

    /** 操作系统版本（如 "Android 14" / "iOS 17.2"；client body 上报） */
    @Column(name = "os_version", length = 32)
    private String osVersion;

    // ====== 地区 ======

    /** 客户端界面语言（BCP-47，如 "zh-CN" / "en-US"；来自 X-Client-Lang header） */
    @Column(name = "locale", length = 16)
    private String locale;

    /**
     * 地区码（如 "CN" / "US"）。
     * <p>V1：客户端上报 {@code Locale.getDefault().getCountry()} 兜底；
     * <p>V2：服务端拦截器用 GeoIP 解析真实出口 IP 后覆盖此字段（更准 / 防伪）。
     */
    @Column(name = "region", length = 16)
    private String region;

    /** 设备时区（如 "Asia/Shanghai"；client body 上报） */
    @Column(name = "timezone", length = 32)
    private String timezone;

    /** 网络类型（wifi/4g/5g/unknown；预留字段，client 可暂不上报） */
    @Column(name = "network_type", length = 16)
    private String networkType;

    // ====== 时间 ======

    /**
     * 客户端事件发生时刻（毫秒时间戳，UTC）。
     * <p>来源：{@code Date.now()} —— 不可信（设备时间可被改），仅做客户端时序排查参考。
     */
    @Column(name = "client_ts", nullable = false)
    private Long clientTs;

    /**
     * 服务端落库时刻（毫秒时间戳，UTC）。
     * <p>所有时间维度索引/查询都基于此字段，是权威时间。
     */
    @Column(name = "server_ts", nullable = false)
    private Long serverTs;

    // ====== 业务 ======

    /**
     * 业务自定义属性（扁平 KV）。
     * <p>客户端约定值都用 String/数字字符串，避免嵌套结构；
     * <p>{@link JsonMapConverter} 自动序列化为 JSON 字符串存进 TEXT 列。
     */
    @Convert(converter = JsonMapConverter.class)
    @Column(name = "properties", columnDefinition = "TEXT")
    private Map<String, String> properties = new HashMap<>();
}
