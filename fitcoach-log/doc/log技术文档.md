# fitcoach-log 模块技术文档

## 一、模块定位

客户端日志远程拉取能力。运营 / 研发在 admin 后台输入用户 uid 创建任务，客户端轮询发现任务后把本地日志打成 zip 上传，admin 即可下载排查问题。

整套链路完全走 HTTP，无需 WebSocket / FCM / 长链接。

## 二、状态机

```
                                    ┌── PENDING (admin 创建)
                                    │
       客户端轮询命中（logTask）    │  scheduler 24h 未被领走
       同事务 PESSIMISTIC_WRITE     │      ▼
       原子改 UPLOADING             │   EXPIRED
                                    ▼
                                 UPLOADING
                                    │
                ┌─上传成功─────────┼──失败 retryCount<3──回滚 PENDING
                │                  │
                ▼                  ├──失败 retryCount=3─→ FAILED
            UPLOADED               │
                │                  └──5 分钟超时─→ scheduler 同上
                │
                └─7 天后 scheduler 删盘 → EXPIRED（行保留作审计）
```

枚举见 [`LogPullStatus`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/entity/LogPullStatus.java)。

## 三、核心类清单

| 类 | 职责 |
|----|----|
| [`LogPullTask`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/entity/LogPullTask.java) | 任务实体（含 status / assignedAt / uploadedAt / expireAt / retryCount / fileRelativePath） |
| [`LogPullTaskRepository`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/repository/LogPullTaskRepository.java) | 数据访问层；`lockTopPendingForUid` 用 PESSIMISTIC_WRITE 行锁防并发分配 |
| [`LogPullService`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/service/LogPullService.java) | 三大动作：admin 创建 / 客户端 claim / 客户端 upload；状态机推进与重试限制 |
| [`LogStorageService`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/service/LogStorageService.java) | 本地磁盘读写；含路径越界保护 |
| [`LogTaskScheduler`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/service/LogTaskScheduler.java) | 三类周期扫描（默认每 60s 一轮） |
| [`AdminLogController`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/controller/AdminLogController.java) | `/api/admin/logs/tasks/**` |
| [`UserLogController`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/controller/UserLogController.java) | `/api/logs/**` |
| [`LogProperties`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/config/LogProperties.java) | 配置承载（all in `log-pull.*`） |

## 四、HTTP 接口契约

### 4.1 admin 后台（需登录，走 AdminAuthInterceptor）

#### POST `/api/admin/logs/tasks`

创建一条 PENDING 任务。

请求体：

```json
{
  "uid": "u_a1b2c3...",
  "remark": "用户反馈卡顿，需要排查",
  "recentHours": 24
}
```

返回：`Result<LogTaskDto>`

错误码：
- `7303` 目标用户不存在
- `7304` 24h 内已存在未完成任务（防短时间反复创建）

#### GET `/api/admin/logs/tasks?uid=xxx&status=PENDING&page=1&size=20`

按 uid 必填 + status 可选分页查询。返回 `PageVO<LogTaskDto>`，结构与 fitcoach-admin 的 `PageResponse` 完全一致。

#### GET `/api/admin/logs/tasks/{id}`

详情。

#### GET `/api/admin/logs/tasks/{id}/download`

流式下载 zip。仅当 `status=UPLOADED` 才允许；否则 7305。

文件名格式：`fitcoach_log_<uid>_<taskId>.zip`，Content-Disposition 走 RFC 5987 编码兼容中文。

错误码：
- `7305` 任务尚未上传完成
- `7306` 文件已被清理或不存在
- `7322` 读取文件 IO 错误

#### DELETE `/api/admin/logs/tasks/{id}`

删除任务（同步删盘 zip）。

### 4.2 客户端（需登录，走 AuthService）

#### 拉取 PENDING 任务

由 fitcoach-clientbus 模块的通用轮询入口 `GET /api/client/poll` 提供，本模块通过实现
`ClientPollContribution`（见 `LogPullContribution`）把 PENDING 任务以 `logTask` 字段贡献到
该入口的响应中，**不再单独暴露 `/api/logs/pending`**。

底层仍是 `LogPullService.claimNextPending`：**同事务**用 PESSIMISTIC_WRITE 锁取 PENDING →
改 UPLOADING → 写 assignedAt。

响应（命中）：

```json
{
  "code": 0,
  "data": {
    "logTask": {
      "taskId": 123,
      "recentHours": 24,
      "expireAtMillis": 1704096000000,
      "uploadingDeadlineMillis": 1704009600000
    },
    "serverTime": 1717000000000
  }
}
```

响应（未命中）：`data` 中不出现 `logTask` 字段。

客户端按 120s 周期轮询；命中后立即开始打包/上传。

#### POST `/api/logs/upload` （multipart/form-data）

字段：
- `taskId` (form, Long)
- `file` (form, MultipartFile, application/zip)

幂等规则（详见 [`LogPullService.acceptUpload`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/service/LogPullService.java:172-220)）：

| task.status | 处理 |
|------|------|
| `UPLOADED` | 直接返成功 + `idempotent=true`；客户端可清队列 |
| `UPLOADING` | 写盘 + 状态改 UPLOADED |
| 其他 | `7315` 拒收 |

错误码：`7311/7312/7313/7314/7315/7316`。

#### POST `/api/logs/tasks/{id}/fail`

客户端打包/上传遇到不可恢复错误时主动回报。

请求体：`{ "reason": "zip_failed: ENOENT" }`

服务端按 `retryCount + 1 < maxRetryCount` 回滚 PENDING，否则标 FAILED。

## 五、关键设计决策

### 5.1 防并发分配（同 uid 多设备同时 pull）

[`LogPullTaskRepository.lockTopPendingForUid`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/repository/LogPullTaskRepository.java:36-51) 在 `@Transactional` 内用 `LockModeType.PESSIMISTIC_WRITE` 锁住该 uid 最早的 PENDING 行；同一时刻只有一个事务能拿到，其他事务在锁释放后会因为状态已变 UPLOADING 而拿不到此行（再次 query），从而落到 `Optional.empty()`。

### 5.2 幂等上传（客户端重传 / 网络抖动）

服务端状态机做主防线（status 已 UPLOADED 直接返成功），客户端本地 `processed_log_task_ids` SharedPreferences 做副防线（同 taskId 永不重复打包）。详见 RN 端 logger 模块文档。

### 5.3 UPLOADING 卡死回滚

客户端 crash / 应用被杀，UPLOADING 状态会卡住。Scheduler 每 60s 扫描 `assignedAt < now - 5min` 的任务，retryCount + 1 后回滚 PENDING（达上限标 FAILED）。

### 5.4 24h 内同 uid 限创 1 条

避免运营误操作短时间反复创建同一用户的任务堆积；通过 `countActiveByUidSince` 校验。配置 `log-pull.max-active-task-per-uid-in-24h` 可调。

### 5.5 流式下载

[`AdminLogController.download`](fitcoach-log/src/main/java/com/lanprojects/fitcoach/log/controller/AdminLogController.java:91-126) 用 `FileSystemResource` —— Spring 自动走 zero-copy 流式输出，不会一次性把 50MB 加载到内存。

## 六、配置说明（application.yml）

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 60MB           # 比 log-pull.max-upload-size-bytes 略大留 buffer
      max-request-size: 60MB

log-pull:
  sub-dir: logs                     # 在 upload.base-dir 下的子目录
  max-upload-size-bytes: 52428800   # 50MB 单包上限
  pending-expire-hours: 24
  uploading-timeout-minutes: 5
  uploaded-retention-days: 7
  max-retry-count: 3
  scheduler-interval-seconds: 60
  max-active-task-per-uid-in-24h: 1
```

## 七、错误码段（7301-7399）

见 [`ResultCode`](fitcoach-common/src/main/java/com/lanprojects/fitcoach/common/model/ResultCode.java)。

| 码 | 含义 |
|----|----|
| 7301 | 任务不存在 |
| 7302 | status 枚举不合法 |
| 7303 | 目标用户不存在 |
| 7304 | 24h 内已有未完成任务 |
| 7305 | 任务尚未上传完成（不可下载） |
| 7306 | 文件已被清理或不存在 |
| 7307 | 重试次数已达上限 |
| 7308 | 任务已被其他设备领取 |
| 7311 | 上传文件为空 |
| 7312 | 上传文件过大 |
| 7313 | contentType 不合法 |
| 7314 | 任务归属用户不匹配 |
| 7315 | 当前任务状态不允许上传 |
| 7316 | 文件保存失败 |
| 7321 | 任务已过期 |
| 7322 | 文件读取失败 |

## 八、部署 / 运维提示

1. **目录权限**：`upload.base-dir` 下的 `logs/` 子目录会按需创建，确保运行账号有读写权限；
2. **磁盘容量**：单 zip 50MB，UPLOADED 保留 7 天，按 1000 用户 / 月 1 次估算 ≈ 350GB / 月，需关注磁盘配额；
3. **多实例部署**：当前 scheduler **未加分布式锁**，仅适用于单实例。多实例需要换 ShedLock；
4. **DB 索引**：实体已配 `idx_log_uid_status` / `idx_log_status_assigned` / `idx_log_status_uploaded`，三类高频 query 都能走 index；
5. **故障排查**：所有关键节点都打了 INFO/WARN 日志，关注关键字 `[LogScheduler]` / `日志任务` / `日志上传`。

## 九、未来扩展

- 切 OSS：把 `LogStorageService` 抽接口，再加一个 `OssLogStorageService` 即可，业务层无感；
- 多文件：当前一次任务对应一个 zip；后续若需要分片上传，DTO 加 `chunkIndex/totalChunks`；
- 下载 URL 签名：当前 download 走管理员鉴权 + 任务表查；后续可加预签名 URL 直链 OSS。
