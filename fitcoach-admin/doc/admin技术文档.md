# FitCoach Server — 后台管理模块技术文档（fitcoach-admin）

> 给 admin 后端开发用的完整接口参考。
> 三端协作 / 会员支付 / 客户端日志拉取等专题在 [`../../doc/`](../../doc) 下的独立文档。

---

## 1. 模块定位

后台管理模块（`fitcoach-admin`）为 [`FitCoachAdminManager`](../../../FitCoachAdminManager) 前端提供
**所有运营 + 配置管理** 能力。和客户端业务模块（login/feedback/exercise/membership/payment/appversion/log）
共用同一份数据库与服务，但走完全独立的鉴权链路：

| 维度 | 客户端 | 管理后台 |
|------|--------|---------|
| 账号载体 | `user`（uid + openId/phone/email） | `admin_user`（username + 密码哈希） |
| 登录方式 | 微信 / 手机号 / 密码 / 邮箱 / Google / Apple | 账号 + 密码（BCrypt） |
| Token type | `access` / `refresh` | `admin_access` |
| JWT 工具 | [`JwtUtils`](../../fitcoach-login/src/main/java/com/lanprojects/fitcoach/login/util/JwtUtils.java) | [`AdminJwtUtils`](../src/main/java/com/lanprojects/fitcoach/admin/util/AdminJwtUtils.java) |
| Token 过期 | access 2h / refresh 7d | 8h |
| 拦截器 | `ClientInfoInterceptor`（解析 client 头） | [`AdminAuthInterceptor`](../src/main/java/com/lanprojects/fitcoach/admin/security/AdminAuthInterceptor.java) |
| 单设备互踢 | 是（sid claim） | 否（管理员可多端） |

签名密钥 (`jwt.secret`) 共用，但 token `type` claim 不同：
**客户端 token 进 admin 拦截器会因 `type != admin_access` 被拒，反之亦然**。

---

## 2. 路由总览（11 个 Controller / 约 40 个接口）

所有接口前缀 `/api/admin/**`，由 [`AdminAuthInterceptor`](../src/main/java/com/lanprojects/fitcoach/admin/security/AdminAuthInterceptor.java) 统一鉴权。
登录接口 `/api/admin/auth/login` 通过 [`AdminWebMvcConfig`](../src/main/java/com/lanprojects/fitcoach/admin/security/AdminWebMvcConfig.java) 的 `excludePathPatterns` 放行。

### 2.1 鉴权 / 自我管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|-----|
| POST | `/api/admin/auth/login` | 管理员登录 | 公开 |
| GET  | `/api/admin/auth/me` | 当前管理员资料 | 任意角色 |
| PUT  | `/api/admin/auth/password` | 修改自己密码 | 写（VIEWER 拒） |

### 2.2 Dashboard

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|-----|
| GET | `/api/admin/dashboard/overview` | 用户/反馈/收入聚合 | 任意角色 |

### 2.3 用户管理（`AdminUserController`）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|-----|
| GET | `/api/admin/users` | 分页（keyword/enabled/loginType） | 任意 |
| GET | `/api/admin/users/{uid}` | 详情 | 任意 |
| PUT | `/api/admin/users/{uid}/status` | 启用/禁用 | 写 |

### 2.4 反馈管理（`AdminFeedbackController`）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|-----|
| GET | `/api/admin/feedbacks` | 分页（status/type/keyword/时间区间） | 任意 |
| GET | `/api/admin/feedbacks/{id}` | 详情 | 任意 |
| PUT | `/api/admin/feedbacks/{id}/status` | 状态流转 + 处理回复 | 写 |

### 2.5 肌群管理（`AdminMuscleGroupController`）

> 路径前缀 `/api/admin/muscle-groups`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|-----|
| GET    | `/` | 列表（含禁用，按 sortOrder） | 任意 |
| GET    | `/{id}` | 详情 | 任意 |
| POST   | `/` | 创建 | 写 |
| PATCH  | `/{id}` | 更新（groupKey 不可改） | 写 |
| POST   | `/{id}/toggle-enabled?value=true|false` | 上下架 | 写 |
| DELETE | `/{id}` | 硬删除（有 Exercise 引用 → 7603） | 写 |

### 2.6 动作管理（`AdminExerciseController`）

> 路径前缀 `/api/admin/exercises`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|-----|
| GET    | `/` | 列表（含禁用） | 任意 |
| GET    | `/{id}` | 详情 | 任意 |
| POST   | `/` | 创建 | 写 |
| PATCH  | `/{id}` | 更新 | 写 |
| POST   | `/{id}/toggle-free?value=true|false` | 切免费/付费 | 写 |
| POST   | `/{id}/toggle-enabled?value=true|false` | 上下架 | 写 |
| DELETE | `/{id}` | 硬删除（每肌群必须保留 ≥ 1 个免费动作 → 7504） | 写 |

### 2.7 会员套餐管理（`AdminMembershipPlanController`）

> 路径前缀 `/api/admin/membership/plans`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|-----|
| GET | `/` | 列表（含禁用） | 任意 |
| GET | `/{id}` | 详情 | 任意 |
| POST | `/` | 创建套餐 | 写 |
| PATCH | `/{id}` | 更新（planCode 不可改） | 写 |
| POST | `/{id}/toggle?enabled=true|false` | 上下架（不提供删除） | 写 |

### 2.8 用户会员状态（`AdminUserMembershipController`）

> 路径前缀 `/api/admin/membership/users`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|-----|
| GET  | `/{uid}` | 查单个用户会员状态 | 任意 |
| POST | `/{uid}/grant` | 手动赠送/续费 N 天 | 写 |
| POST | `/{uid}/revoke` | 立即撤销 | 写 |
| POST | `/batch` | 批量查询 uid → 会员状态 | 任意 |

### 2.9 支付订单管理（`AdminPaymentOrderController`）

> 路径前缀 `/api/admin/payment/orders`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|-----|
| GET | `/` | 分页（按创建倒序，可过 status） | 任意 |
| GET | `/{orderId}` | 详情（含 user uid/nickname） | 任意 |
| POST | `/{orderId}/refund` | 标记退款（V1 仅记账，钱财务线下退） | 写 |

### 2.10 版本管理（`AdminAppVersionController`）

> 路径前缀 `/api/admin/app-versions`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|-----|
| GET    | `/?platform=android|ios` | 列表（含未发布草稿） | 任意 |
| GET    | `/{id}` | 详情 | 任意 |
| POST   | `/` | 创建（默认 isPublished=false） | 写 |
| PATCH  | `/{id}` | 更新（platform / versionCode 不可改） | 写 |
| POST   | `/{id}/toggle-published?value=true|false` | 发布/下线 | 写 |
| DELETE | `/{id}` | 硬删除 | 写 |

### 2.11 系统配置（`AdminSysConfigController`）

> 路径前缀 `/api/admin/sys-config`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|-----|
| GET  | `/?group=xxx` | 配置列表（可按分组筛） | 任意 |
| GET  | `/groups` | 所有分组名 | 任意 |
| PUT  | `/{configKey}` | 更新值（明文，server 自动加密） | 写 |
| POST | `/refresh-cache` | 手动刷新内存缓存 | 写 |

> **加密字段**（`encrypted=true`）在 GET 列表中 configValue 显示为 `******`，
> PUT 时如传 `******` 则跳过值更新；传明文则 server 自动加密入库 + 缓存刷新。

---

## 3. 鉴权设计

### 3.1 拦截流程

[`AdminAuthInterceptor.preHandle`](../src/main/java/com/lanprojects/fitcoach/admin/security/AdminAuthInterceptor.java)：

```
1. 读 Authorization 头 → 校验 Bearer 格式（缺 / 错 → 7001 NOT_LOGIN）
2. AdminJwtUtils.parseAndVerify(token, secret)
   ├─ 签名校验
   ├─ type 必须 = admin_access （否则 7002 INVALID_TOKEN）
   └─ 过期校验
3. AdminAuthService.requireAdmin(username)
   ├─ DB 查 admin_user
   └─ 校验 enabled=true（禁用立即生效 → 7004 ADMIN_DISABLED）
4. 写操作（POST/PUT/PATCH/DELETE）+ VIEWER 角色 → 7008 FORBIDDEN
5. 把 username/role 写入 request attribute
   - ATTR_ADMIN_USERNAME → controller 用 (String) request.getAttribute(...) 取
   - ATTR_ADMIN_ROLE
```

> 写入 attribute 而非 ThreadLocal，避免清理遗忘。

### 3.2 角色矩阵

| 角色 | 描述 | 读 | 写 |
|------|------|----|----|
| SUPER_ADMIN | 超级管理员（默认初始账号） | ✓ | ✓ |
| ADMIN | 普通管理员 | ✓ | ✓ |
| VIEWER | 只读账号（运营/产品） | ✓ | ✗ |

> VIEWER 当前无法自助改密；需 SUPER_ADMIN 直接 SQL 重置。

---

## 4. 数据模型

### 4.1 admin_user（admin 模块自管）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(64) | 登录用户名，唯一索引 `uk_admin_username` |
| password_hash | VARCHAR(100) | BCrypt 哈希 |
| display_name | VARCHAR(100) | Header 显示用 |
| role | VARCHAR(32) | SUPER_ADMIN / ADMIN / VIEWER |
| enabled | BOOLEAN | 禁用立即生效（无 token cache） |
| last_login_at | DATETIME | 最近登录 |
| created_at / updated_at | DATETIME | 来自 BaseEntity |

### 4.2 复用的业务表（admin 端只是消费方）

| 模块 | 表 | admin 端做什么 |
|------|----|---------------|
| login | `user` | 查 + 改 enabled + 详情 |
| feedback | `user_feedback` | 查 + 改 status + 写 handler_admin/handler_reply |
| exercise | `exercise` / `muscle_group` | CRUD（写依赖 free/enabled 保护规则） |
| membership | `membership_plan` / `user_membership` | 套餐 CRUD + 手动赠送/撤销 |
| payment | `payment_order` | 列表/详情/标记退款 |
| appversion | `app_version` | 版本管理 + 发布草稿 |
| common | `sys_config` | 直接 CRUD + 缓存刷新 |

### 4.3 反馈状态机（不变）

```
PENDING（新提交，默认值）
   ├──► PROCESSING ──► RESOLVED
   │                └─► IGNORED
   ├──► RESOLVED   （跳过 PROCESSING 直接结案）
   └──► IGNORED
```

可逆：管理员误操作可从 RESOLVED 回 PROCESSING。

---

## 5. 错误码（admin 段：7001-7099 + 业务段）

> 完整列表见 [`ResultCode`](../../fitcoach-common/src/main/java/com/lanprojects/fitcoach/common/model/ResultCode.java)

### 5.1 admin 鉴权（7001-7099）

| 码 | 含义 |
|----|------|
| 7001 | 未登录或登录已过期 |
| 7002 | 无效的管理员凭证（type 不对 / 篡改） |
| 7003 | 账号或密码错误（不区分用户名/密码错） |
| 7004 | 管理员账号已被禁用 |
| 7005 | 管理员账号不存在 |
| 7006 | 密码长度不在 6-32 之间 |
| 7007 | 原密码不正确 |
| 7008 | 权限不足（VIEWER 写操作） |

### 5.2 反馈 / 用户（7101-7299）

| 码 | 含义 |
|----|------|
| 7101 | 反馈记录不存在 |
| 7102 | 反馈状态值不合法 |
| 7201 | 目标用户不存在 |

### 5.3 业务模块段（admin 接口也会返回）

| 码段 | 模块 |
|------|------|
| 7301-7399 | 客户端日志拉取（log 模块） |
| 7501-7599 | 动作 / 会员 / 套餐（exercise/membership） |
| 7601-7699 | 肌群（muscle_group） |
| 8001-8199 | 支付（payment） |

例：
- `7504 EXERCISE_LAST_FREE_IN_GROUP`：删除/取消免费时违反"每肌群至少 1 免费"
- `7603 MUSCLE_GROUP_HAS_EXERCISES`：删除肌群但仍有动作引用
- `SYS_CONFIG_NOT_FOUND`：sys_config 表无此 key

---

## 6. 关键 API 示例

### 6.1 登录

```
POST /api/admin/auth/login
Content-Type: application/json

Request:
{ "username": "admin", "password": "admin123" }

Response.data:
{
  "username": "admin",
  "displayName": "超级管理员",
  "role": "SUPER_ADMIN",
  "token": "eyJ...",
  "expiresIn": 28800
}
```

### 6.2 手动赠送会员

```
POST /api/admin/membership/users/{uid}/grant
Body: { "planCode": "MONTHLY", "days": 30, "reason": "客服补偿" }

行为：
- planCode 必须为 MembershipPlan 表中已启用的套餐
- days 天数（不要求整月，可任意正整数）
- last_order_id 落库格式 GIFT_<timestamp>_<operator>
- 续费叠加：expireAt = max(now, currentExpireAt) + days
```

### 6.3 标记退款

```
POST /api/admin/payment/orders/{orderId}/refund
Body: { "refundCents": 9900, "reason": "用户申诉" }

行为：
- order.status → REFUNDED, refundStatus → COMPLETED
- 不自动撤销会员；如需撤销请操作员手动 POST /membership/users/{uid}/revoke
- V1 不调通道实际退款，钱由财务线下退
```

### 6.4 反馈状态流转

```
PUT /api/admin/feedbacks/{id}/status
Body: { "status": "RESOLVED", "handlerReply": "已修复" }

Service 自动写：
- handlerAdmin = 当前 token 的 username
- handledAt   = now()
```

### 6.5 sys_config 更新

```
PUT /api/admin/sys-config/wechat.app_secret
Body: { "configValue": "明文新值" }

行为（encrypted=true 的 key）：
- server 自动 AES 加密存库
- 同步 sysConfigService.refreshCache() → 内存缓存立即生效
- 如果 configValue 传 "******" → 跳过值更新（前端未改）
```

---

## 7. 默认账号

[`AdminDataInitializer`](../src/main/java/com/lanprojects/fitcoach/admin/config/AdminDataInitializer.java) 在首次启动时创建：

| 字段 | 值 |
|------|----|
| username | `admin` |
| password | `admin123` |
| role | `SUPER_ADMIN` |
| displayName | `超级管理员` |

> **生产首次登录必须立即改密**。

后续新增管理员目前没暴露 API；直接 SQL 插：

```sql
-- BCrypt 哈希可用 BCryptPasswordEncoder.encode("xxx") 单测生成
INSERT INTO admin_user(username, password_hash, display_name, role, enabled, created_at, updated_at)
VALUES ('viewer1', '$2a$10$...', '只读账号', 'VIEWER', true, NOW(), NOW());
```

---

## 8. 目录结构

```
fitcoach-admin/
├── pom.xml                           # 依赖 common/login/feedback/exercise/membership/
│                                     #     payment/appversion/log + spring-security-crypto + jjwt
├── doc/
│   └── admin技术文档.md              # 本文档
└── src/main/java/com/lanprojects/fitcoach/admin/
    ├── config/
    │   └── AdminDataInitializer      # 默认管理员账号初始化
    ├── controller/                   # 11 个 controller
    │   ├── AdminAuthController       # /api/admin/auth/*
    │   ├── AdminUserController       # /api/admin/users/*
    │   ├── AdminFeedbackController   # /api/admin/feedbacks/*
    │   ├── DashboardController       # /api/admin/dashboard/*
    │   ├── AdminMuscleGroupController # /api/admin/muscle-groups/*
    │   ├── AdminExerciseController   # /api/admin/exercises/*
    │   ├── AdminMembershipPlanController # /api/admin/membership/plans/*
    │   ├── AdminUserMembershipController # /api/admin/membership/users/*
    │   ├── AdminPaymentOrderController   # /api/admin/payment/orders/*
    │   ├── AdminAppVersionController # /api/admin/app-versions/*
    │   └── AdminSysConfigController  # /api/admin/sys-config/*
    ├── dto/                          # 分子目录组织（exercise/membership/musclegroup/
    │   ├── appversion/               #              payment/sysconfig/appversion）
    │   ├── exercise/
    │   ├── membership/
    │   ├── musclegroup/
    │   ├── payment/
    │   ├── sysconfig/
    │   ├── AdminLoginRequest/Response/Profile
    │   ├── ChangePasswordRequest
    │   ├── DashboardOverviewDto
    │   ├── FeedbackSummary/Detail/UpdateStatus
    │   ├── PageResponse              # 通用分页信封
    │   └── UserSummary/Detail/UpdateStatus
    ├── entity/
    │   ├── AdminRole                 # SUPER_ADMIN / ADMIN / VIEWER + canWrite()
    │   └── AdminUser
    ├── repository/
    │   └── AdminUserRepository
    ├── security/
    │   ├── AdminAuthInterceptor      # 全路径鉴权 + 角色拦截
    │   └── AdminWebMvcConfig         # 注册拦截器 + 排除 /auth/login
    │   # BCryptPasswordEncoder Bean 由 fitcoach-login.PasswordEncoderConfig 暴露
    ├── service/
    │   ├── AdminAuthService          # 登录/改密/资料
    │   ├── AdminUserService          # 用户列表/详情/启禁
    │   ├── AdminFeedbackService      # 反馈列表/详情/状态流转
    │   ├── DashboardService          # 概览聚合
    │   └── AdminUrlService           # URL 拼接（不在 server 拼绝对 URL）
    └── util/
        └── AdminJwtUtils             # 独立 admin token（type=admin_access）
```

---

## 9. 与 AdminManager 前端的协议

- **baseURL**：前端通过 `VITE_API_BASE_URL` 配置，所有请求拼此前缀
- **图片 URL 拼接**：server 只返相对路径（如 `/static/avatar/x.jpg`），前端用 `baseURL + 相对路径` 拼绝对 URL；不在 server 拼是为了避免被反代 Host 头伪造。详见 [`AdminUrlService`](../src/main/java/com/lanprojects/fitcoach/admin/service/AdminUrlService.java) 注释
- **Token 持久化**：建议 localStorage 存 `token` + `role` + `displayName`，路由层守卫读取
- **错误码处理**：
  - HTTP 401 或 business 7001 → 跳登录页
  - 7008 → toast"权限不足"，不跳页
  - 业务码（7501/7603/8001 等）→ 按 message 展示给运营

---

## 10. 安全审计清单

| 项 | 实现 |
|----|------|
| 密码不入日志 | `AdminAuthService.login` 失败统一报 `ADMIN_LOGIN_FAILED`，不区分账号/密码错 |
| 密码哈希 | BCrypt strength=10（由 fitcoach-login 的 [`PasswordEncoderConfig`](../../fitcoach-login/src/main/java/com/lanprojects/fitcoach/login/config/PasswordEncoderConfig.java) 提供） |
| Token 隔离 | `type=admin_access` claim 强制隔离 user/admin token |
| 禁用立即生效 | 每次请求 `requireAdmin` 二次查 DB |
| 写操作权限 | VIEWER 一律拒（拦截器层） |
| 手机号脱敏 | 用户列表脱敏（前 3 + 末 4），详情才返完整 |
| URL 注入 | 不在 server 拼绝对 URL，避免反代 Host 头伪造 |
| 加密配置 | `encrypted=true` 的 sys_config 列表显示 `******`，仅明文提交才覆盖 |
| 操作员审计 | controller 关键写操作 INFO 打 operator + 目标 id + 关键字段 |

---

## 11. 扩展指南

### 11.1 加超管管理界面（新增管理员）

1. 新建 `AdminUserAdminService`
2. 接口 `POST /api/admin/admins`、`PUT /api/admin/admins/{id}/role`，**仅 SUPER_ADMIN** 可访问
3. 在拦截器之外再加细粒度角色注解（如 `@RequireRole(SUPER_ADMIN)`），controller 入口校验

### 11.2 加 Dashboard 时序图表

- 当前 `DashboardService` 只返总量
- 加 `GET /dashboard/users/trend?days=30`、`/dashboard/payment/trend?days=30`，service 按天 group by
- 数据量大后做日预聚合表（admin_dashboard_daily）

### 11.3 加批量操作（如批量启禁用户）

- DTO 引入 `List<String> uids` + action
- service 用 `userRepository.findByUidIn(...)` 批量加载后流式处理
- 务必加分页或上限（如 100）避免一次操作过大事务

---

## 12. Git 仓库

| 项 | 值 |
|----|----|
| Server 仓库 | https://github.com/RicherLan/FitCoachServer.git |
| Admin 前端仓库 | git@github.com:RicherLan/FitCoachAdminManager.git |

## 13. 相关文档

| 文档 | 说明 |
|------|------|
| [`../../doc/项目技术文档.md`](../../doc/项目技术文档.md) | 服务端总览（10 模块） |
| [`../../doc/会员支付集成.md`](../../doc/会员支付集成.md) | 会员/支付三端协作 |
| [`../../fitcoach-login/doc/login技术文档.md`](../../fitcoach-login/doc/login技术文档.md) | 登录模块 |
| [`../../fitcoach-log/doc/log技术文档.md`](../../fitcoach-log/doc/log技术文档.md) | 日志拉取模块 |
| [`../../../FitCoachAdminManager/doc/管理后台技术文档.md`](../../../FitCoachAdminManager/doc/管理后台技术文档.md) | 前端文档 |
