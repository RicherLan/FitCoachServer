# FitCoach 埋点系统 - Server 端技术文档

## 概述

FitCoach Server 端埋点系统负责：

1. **接收客户端上报的埋点事件** — POST /api/track/batch
2. **校验和限流** — 防止恶意刷量
3. **落库和索引** — 支持后续分析查询
4. **基础信息补全** — userId / region / serverTs 等

## 快速开始

### 1. 接口定义

**请求**：

```
POST /api/track/batch
Content-Type: application/json
Authorization: Bearer {accessToken} (可选)
X-Client-Platform: android / ios
X-Client-Device-Id: {deviceId}
X-Client-Lang: zh-CN / en / ...

{
  "events": [
    {
      "eventKey": "home_view",
      "sessionId": "5e7b9c4f-8a2d-4c91-b3e5-7a8b9c0d1e2f",
      "clientTs": 1704067200000,
      "osVersion": "Android 14",
      "timezone": "Asia/Shanghai",
      "region": "CN",
      "networkType": "wifi",
      "properties": {
        "source": "tab",
        "duration": "120"
      }
    }
  ]
}
```

**响应**：

```json
{
  "code": 0,
  "message": "success",
  "data": 1  // 实际入库的事件数
}
```

### 2. 错误码

| Code | 说明 | 处理建议 |
|------|------|--------|
| 0 | 成功 | - |
| 8401 | 批次为空 | 客户端 bug，检查 SDK 调度器 |
| 8402 | 单批超过 100 条 | 客户端应拆批后重试 |
| 8403 | eventKey 不合法 | 客户端 bug，检查 TrackEvent enum |
| 8404 | 限流触发 | 客户端应延后重试（建议 60s+） |

## 数据模型

### TrackEventEntity

```java
@Entity
@Table(name = "track_event", indexes = {
    @Index(name = "idx_event_ts",   columnList = "event_key, server_ts"),
    @Index(name = "idx_user_ts",    columnList = "user_id, server_ts"),
    @Index(name = "idx_device_ts",  columnList = "device_id, server_ts"),
    @Index(name = "idx_session",    columnList = "session_id"),
    @Index(name = "idx_region_ts",  columnList = "region, server_ts")
})
public class TrackEventEntity extends BaseEntity {
    // 事件 key（蛇形小写）
    private String eventKey;
    
    // 身份
    private String userId;           // 可空（未登录用户）
    private String deviceId;         // 必填
    private String sessionId;        // 必填
    
    // 设备
    private String platform;         // android / ios
    private String appVersion;       // 应用市场版本
    private String bundleVersion;    // JS bundle 版本
    private String osVersion;        // 操作系统版本
    
    // 地区
    private String locale;           // BCP-47 语言标签
    private String region;           // 地区码（CN / US / ...）
    private String timezone;         // 时区
    
    // 网络
    private String networkType;      // wifi / 4g / 5g / unknown
    
    // 时间
    private Long clientTs;           // 客户端时刻（不可信）
    private Long serverTs;           // 服务端时刻（权威）
    
    // 业务
    private Map<String, String> properties; // JSON 列
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| eventKey | String(64) | ✓ | 事件唯一标识 |
| userId | String(64) | ✗ | 用户 ID（未登录为 null） |
| deviceId | String(64) | ✓ | 设备唯一标识 |
| sessionId | String(64) | ✓ | 会话 ID（一次冷启动一个） |
| platform | String(16) | ✓ | android / ios |
| appVersion | String(32) | ✗ | 应用市场版本 |
| bundleVersion | String(32) | ✗ | JS bundle 版本 |
| osVersion | String(32) | ✗ | 操作系统版本 |
| locale | String(16) | ✗ | 客户端界面语言 |
| region | String(16) | ✗ | 地区码（V1 客户端兜底，V2 GeoIP 覆盖） |
| timezone | String(32) | ✗ | 设备时区 |
| networkType | String(16) | ✗ | 网络类型 |
| clientTs | Long | ✓ | 客户端时刻（毫秒） |
| serverTs | Long | ✓ | 服务端时刻（毫秒） |
| properties | Map | ✗ | 业务自定义属性（JSON） |

## 核心逻辑

### TrackService.receiveBatch()

```java
public int receiveBatch(String uid, TrackEventBatchRequest request) {
    // 1. 校验：批次非空、数量 ≤ 100
    if (request == null || request.getEvents().isEmpty()) {
        throw new BusinessException(ResultCode.TRACK_BATCH_EMPTY);
    }
    if (request.getEvents().size() > MAX_BATCH_SIZE) {
        throw new BusinessException(ResultCode.TRACK_BATCH_TOO_LARGE);
    }
    
    // 2. 限流：deviceId 维度 200 批次/分钟
    String deviceId = ClientContext.deviceId();
    if (!rateLimiter.tryAcquire(deviceId)) {
        throw new BusinessException(ResultCode.TRACK_RATE_LIMITED);
    }
    
    // 3. 组装：从 ClientContext 注入基础信息
    String platform = ClientContext.platform();
    String appVersion = ClientContext.get().nativeVersionName();
    String bundleVersion = ClientContext.get().bundleVersionName();
    String locale = ClientContext.lang();
    
    // 4. 落库：批量 saveAll
    List<TrackEventEntity> entities = new ArrayList<>();
    for (TrackEventItem item : request.getEvents()) {
        TrackEventEntity entity = new TrackEventEntity();
        entity.setEventKey(item.getEventKey());
        entity.setUserId(uid);  // 从 token 解析，可空
        entity.setDeviceId(deviceId);
        entity.setSessionId(item.getSessionId());
        entity.setPlatform(platform);
        // ... 其他字段
        entity.setServerTs(System.currentTimeMillis());
        entities.add(entity);
    }
    trackEventRepository.saveAll(entities);
    
    return entities.size();
}
```

### TrackRateLimiter

```java
public class TrackRateLimiter {
    private static final int MAX_PER_WINDOW = 200;  // 200 批次/分钟
    private static final Duration WINDOW = Duration.ofMinutes(1);
    
    public boolean tryAcquire(String deviceId) {
        AtomicInteger counter = store.get(deviceId, k -> new AtomicInteger(0));
        int after = counter.incrementAndGet();
        return after <= MAX_PER_WINDOW;
    }
}
```

## 查询接口

### Repository 方法

```java
// 按用户查事件流
Page<TrackEventEntity> findByUserIdOrderByServerTsDesc(String userId, Pageable pageable);

// 按设备查事件流
Page<TrackEventEntity> findByDeviceIdOrderByServerTsDesc(String deviceId, Pageable pageable);

// 按事件名 + 时间窗查列表
Page<TrackEventEntity> findByEventKeyAndServerTsBetweenOrderByServerTsDesc(
    String eventKey, Long startTs, Long endTs, Pageable pageable);

// 按 sessionId 查完整会话序列
List<TrackEventEntity> findBySessionIdOrderByServerTsAsc(String sessionId);

// 聚合：时间窗内每个 event_key 的 PV + UV
List<EventAggregateProjection> aggregateOverview(Long startTs, Long endTs);

// 聚合：含未登录的 UV（按 deviceId 去重）
List<DeviceUvProjection> aggregateDeviceUv(Long startTs, Long endTs);
```

## 关键设计决策

### 1. serverTs 是权威时间

- 所有时间维度的索引和查询都基于 `serverTs`
- 不信任 `clientTs`（设备时间可被用户修改、跨时区有偏差）
- `clientTs` 仅作为客户端时序排查的参考

### 2. userId 可空

- 未登录用户也允许上报埋点
- 注册前的浏览行为对漏斗分析很有价值
- Controller 对 token 做 try-best 解析，失败时 uid = null

### 3. region 由 server 端 GeoIP 覆盖（V2）

- **V1**：客户端上报 `Locale.getDefault().getCountry()` 兜底
- **V2**：服务端拦截器用 MaxMind GeoLite2 解析真实出口 IP，覆盖更准的值
- 海外商店上线后用于按区域切分分析

### 4. properties 用 JSON 列

- 扁平 KV 结构（避免嵌套 object/array）
- 便于后续 admin 端做 `JSON_EXTRACT` 查询
- 用 `JsonMapConverter` 自动序列化/反序列化

### 5. 脏数据跳过不抛异常

- 单条 item 校验失败（eventKey 空、长度超限）会被静默跳过 + warn 日志
- 不抛异常 —— 单一脏数据不应阻断整批上报
- 否则客户端反复重试会越积越多

## 分区和归档规划

当前 `ddl-auto=update` 不会建分区表。待数据量增长后：

1. **DBA 手动改表结构**：RANGE PARTITION BY server_ts 月度分区
2. **老数据归档**：按月 DETACH 到冷存储（如 S3）
3. **查询优化**：自动走分区裁剪，提升大时间窗查询性能

## 监控和告警

### 关键指标

| 指标 | 说明 | 告警阈值 |
|------|------|--------|
| 上报 QPS | 每秒上报批次数 | > 1000 |
| 限流触发率 | 被限流的批次占比 | > 1% |
| 脏数据率 | 被跳过的事件占比 | > 0.1% |
| 入库延迟 | 从上报到落库的时间 | > 5s |

### 日志关键字

```
FCLog.d(TAG, `event: ${eventKey}, queue_size=${trackQueue.size()}`);
FCLog.w(TAG, `埋点限流触发: deviceId=${deviceId}`);
FCLog.w(TAG, `跳过非法埋点 eventKey: ${key}`);
```

## 常见问题

### Q: 为什么不强制登录？

**A**: 未登录用户的行为（首页浏览、点击注册按钮）对漏斗分析很有价值。强制登录会丢失这部分数据。

### Q: 为什么限流是 200 批次/分钟？

**A**: 客户端 SDK 内置 30s/20 条节流，正常用户远低于此阈值。200 批次/分钟只挡两类异常：客户端 bug 死循环 + 恶意刷量。

### Q: 如何处理多副本部署的限流不一致？

**A**: 当前 V1 单机 Caffeine 限流，多副本部署时总配额是 200*N（N=副本数）。严格全局限流需迁 Redis lua（短期没必要）。

### Q: 如何查询特定用户的埋点？

**A**: 
```java
// 按 userId 查事件流
Page<TrackEventEntity> events = trackEventRepository
    .findByUserIdOrderByServerTsDesc(uid, PageRequest.of(0, 20));

// 按 deviceId 查事件流（含未登录 + 已登录的全部事件）
Page<TrackEventEntity> events = trackEventRepository
    .findByDeviceIdOrderByServerTsDesc(deviceId, PageRequest.of(0, 20));
```

## 相关文件

- [`TrackEventEntity.java`](./src/main/java/com/lanprojects/fitcoach/track/entity/TrackEventEntity.java) — JPA entity
- [`TrackEventRepository.java`](./src/main/java/com/lanprojects/fitcoach/track/repository/TrackEventRepository.java) — 数据访问层
- [`TrackService.java`](./src/main/java/com/lanprojects/fitcoach/track/service/TrackService.java) — 业务逻辑
- [`TrackController.java`](./src/main/java/com/lanprojects/fitcoach/track/controller/TrackController.java) — HTTP 接口
- [`TrackRateLimiter.java`](./src/main/java/com/lanprojects/fitcoach/track/service/TrackRateLimiter.java) — 限流器

## 版本历史

- **v1.0** (2026-01) — Phase 1 上线：基础上报 + 限流 + 落库
- **v2.0** (规划中) — Phase 2：GeoIP region 覆盖 + 分区表 + 聚合查询优化
- **v3.0** (规划中) — Phase 3：性能埋点 + 错误埋点 + 自定义事件
