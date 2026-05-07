# FitCoach Server — 后台管理模块技术文档

## 1. 模块概述

后台管理模块（**fitcoach-admin**）为 `FitCoachAdminManager` 前端提供数据查看与管理能力，
是 FitCoach 项目里"运营/运维侧"的入口，与客户端业务（`fitcoach-login` / `fitcoach-feedback`）共用同一份数据库，
但走完全独立的鉴权链路：管理员账号单独建表、token 类型隔离、权限按角色控制。

### 核心职责

- **管理员认证**：账号密码登录（BCrypt）→ 颁发 admin JWT；支持改密、获取个人资料
- **用户管理**：分页查询用户、查看详情、启用/禁用账号
- **反馈管理**：分页查询反馈、查看详情、状态流转 + 处理回复
- **数据概览**：Dashboard 聚合用户数、反馈数、按状态/类型分布等核心指标
- **权限控制**：SUPER_ADMIN / ADMIN / VIEWER 三档角色，VIEWER 只读

### 业务边界

- **不重复造用户表**：与客户端共用 `user` / `user_feedback` 表，admin 端只做"查 + 改状态"，不创建/删除业务数据
- **不直接管理订单**：项目当前无订单业务，待业务上线后再补充对应接口
- **不暴露敏感字段**：列表手机号脱敏、密码哈希永远不出库

---

## 2. 路由规划

所有接口前缀 `/api/admin/**`，由 [`AdminAuthInterceptor`](../src/main/java/com/lanprojects/fitcoach/admin/security/AdminAuthInterceptor.java) 统一拦截鉴权。
登录接口 `/api/admin/auth/login` 通过 [`AdminWebMvcConfig`](../src/main/java/com/lanprojects/fitcoach/admin/security/AdminWebMvcConfig.java) 的 `excludePathPatterns` 放行。

| 方法 | 路径 | 说明 | 写权限 |
|------|------|------|--------|
| POST | `/api/admin/auth/login` | 管理员登录 | 公开 |
| GET  | `/api/admin/auth/me` | 当前管理员资料 | 任意角色 |
| PUT  | `/api/admin/auth/password` | 修改自己密码 | 写（VIEWER 拒） |
| GET  | `/api/admin/dashboard/overview` | 概览统计 | 任意角色 |
| GET  | `/api/admin/users` | 用户列表（分页 + 多条件） | 任意角色 |
| GET  | `/api/admin/users/{uid}` | 用户详情 | 任意角色 |
| PUT  | `/api/admin/users/{uid}/status` | 启用/禁用用户 | 写 |
| GET  | `/api/admin/feedbacks` | 反馈列表（分页 + 多条件） | 任意角色 |
| GET  | `/api/admin/feedbacks/{id}` | 反馈详情 | 任意角色 |
| PUT  | `/api/admin/feedbacks/{id}/status` | 状态流转 + 处理回复 | 写 |

---

## 3. 鉴权设计（与客户端的隔离）

### 3.1 为什么走独立鉴权

| 维度 | 客户端 | 管理员后台 |
|------|--------|-----------|
| 账号载体 | `user` 表（uid + openId/phone） | `admin_user` 表（username + 密码哈希） |
| 登录方式 | 微信 / 手机号验证码 | 账号 + 密码 |
| Token 类型 | `access` / `refresh` | `admin_access` |
| Service | `AuthService` | `AdminAuthService` |
| Util | `JwtUtils` | `AdminJwtUtils` |
| Token 过期 | access 2h / refresh 7d | 8h（独立配置） |

**核心隔离手段**：JWT 签名密钥 (`jwt.secret`) 共用，但 token 内 `type` claim 不同 ——
客户端 token 进 admin 拦截器会因 `type != admin_access` 被拒，反之亦然，避免误用。

### 3.2 拦截器流程

[`AdminAuthInterceptor.preHandle`](../src/main/java/com/lanprojects/fitcoach/admin/security/AdminAuthInterceptor.java) 顺序：

```
1. 读 Authorization 头 → 校验 Bearer 格式
2. AdminJwtUtils.parseAndVerify(token, secret)
   ├─ 签名校验
   ├─ type 必须 = admin_access
   └─ 过期校验
3. AdminAuthService.requireAdmin(username)
   ├─ DB 查找 admin_user
   └─ 校验 enabled=true（禁用立即生效）
4. 写操作（POST/PUT/PATCH/DELETE）+ VIEWER 角色 → 7008 拒绝
5. 把 username/role 写入 request attribute（controller 取用）
```

### 3.3 角色矩阵

| 角色 | 描述 | 读 | 写 |
|------|------|----|----|
| SUPER_ADMIN | 超级管理员（默认初始账号） | ✓ | ✓ |
| ADMIN       | 普通管理员 | ✓ | ✓ |
| VIEWER      | 只读账号（运营/产品） | ✓ | ✗ |

> ⚠️ VIEWER 角色当前无法自助修改密码（拦截器已拦下 `PUT /password`），
> 需联系 SUPER_ADMIN 直接 SQL 重置 `password_hash`，后续可考虑增加超管"重置他人密码"接口。

---

## 4. 数据模型

### 4.1 admin_user 表（新建）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(64) | 登录用户名（唯一索引 `uk_admin_username`） |
| password_hash | VARCHAR(100) | BCrypt 哈希（60 字符固定，留 buffer） |
| display_name | VARCHAR(100) | 显示名（Header 展示用） |
| role | VARCHAR(32) | SUPER_ADMIN / ADMIN / VIEWER |
| enabled | BOOLEAN | 启用状态 |
| last_login_at | DATETIME | 最后登录时间 |
| created_at / updated_at | DATETIME | 来自 BaseEntity |

### 4.2 user_feedback 表扩展（兼容老数据）

在 [`UserFeedback`](../../fitcoach-feedback/src/main/java/com/lanprojects/fitcoach/feedback/entity/UserFeedback.java) 上加列：

| 字段 | 类型 | 说明 |
|------|------|------|
| status | VARCHAR(32) | PENDING / PROCESSING / RESOLVED / IGNORED；nullable，service 读取兜底为 PENDING |
| handler_admin | VARCHAR(64) | 处理人 username |
| handler_reply | TEXT | 管理员回复（最长 500 字） |
| handled_at | DATETIME | 最近一次状态流转时间 |

新增索引 `idx_status` 支撑后台按状态过滤。

### 4.3 反馈状态机

```
PENDING (新提交，service 默认值)
   ├──► PROCESSING (处理中)
   │       ├──► RESOLVED (已解决)
   │       └──► IGNORED  (已忽略)
   ├──► RESOLVED   (跳过 PROCESSING 直接结案)
   └──► IGNORED    (无效/重复)
```

设计为**可逆**状态机：管理员误操作可从 RESOLVED 改回 PROCESSING；不强制单向流转。

---

## 5. 关键 API 详情

### 5.1 登录

```
POST /api/admin/auth/login
Content-Type: application/json

Request:
{ "username": "admin", "password": "admin123" }

Response:
{
  "code": 0, "message": "success",
  "data": {
    "username": "admin",
    "displayName": "超级管理员",
    "role": "SUPER_ADMIN",
    "token": "eyJ...",
    "expiresIn": 28800
  }
}
```

### 5.2 用户列表

```
GET /api/admin/users?page=1&size=20&keyword=张&enabled=true&loginType=PHONE
Authorization: Bearer {token}

Response: { code, message, data: PageResponse<UserSummaryDto> }
```

`UserSummaryDto` 字段：`uid / nickname / avatarUrl / loginType / gender / phoneMasked / enabled / createdAt / lastLoginAt`。
列表里手机号脱敏（保留首 3 位 + 末 4 位）；详情接口才返回完整手机号。

### 5.3 启用 / 禁用用户

```
PUT /api/admin/users/{uid}/status
Authorization: Bearer {token}
Content-Type: application/json

Body: { "enabled": false }
```

禁用后客户端在 `JwtUtils + AuthService.getCurrentUser` 二次落地校验时即被拒（`USER_DISABLED 3002`），
无需等客户端 token 自然过期。

### 5.4 反馈列表

```
GET /api/admin/feedbacks?page=1&size=20&status=PENDING&type=EXPERIENCE
   &keyword=登录&start=1700000000000&end=1800000000000
Authorization: Bearer {token}
```

- `keyword` 在 content / uid 上模糊匹配
- `start` / `end` 是创建时间区间（毫秒，含 start，不含 end）
- 列表项 `contentPreview` 截断到 80 字 + …，附件只返回 `attachmentCount`
- **反 N+1**：本页 uid 收集后一次 `userRepository.findByUidIn(...)` 批量回填昵称

### 5.5 反馈状态流转

```
PUT /api/admin/feedbacks/{id}/status
Authorization: Bearer {token}
Content-Type: application/json

Body: { "status": "RESOLVED", "handlerReply": "已修复，下个版本生效" }
```

- `status` 必填；`handlerReply` 可选（传 null 不修改、传 "" 清空）
- service 自动写入 `handlerAdmin = 当前 token 的 username` + `handledAt = now()`

### 5.6 Dashboard 概览

```
GET /api/admin/dashboard/overview
Authorization: Bearer {token}

Response.data:
{
  "totalUsers": 12345, "activeUsers": 11000,
  "newUsersToday": 23, "newUsersLast7Days": 180, "newUsersLast30Days": 720,
  "totalFeedbacks": 320,
  "pendingFeedbacks": 18, "processingFeedbacks": 5,
  "resolvedFeedbacks": 290, "ignoredFeedbacks": 7,
  "feedbacksByType": { "SUGGESTION": 120, "EXPERIENCE": 180, "OTHER": 20 },
  "serverTime": 1733299200000
}
```

> 当前走 SQL count 实时聚合，数据量不大可接受；用户量上来后可加 Redis cache 或预聚合表。

---

## 6. 错误码（统一在 [`ResultCode`](../../fitcoach-common/src/main/java/com/lanprojects/fitcoach/common/model/ResultCode.java) 中维护）

| 码 | 含义 |
|----|------|
| 7001 | 管理员未登录或登录已过期 |
| 7002 | 无效的管理员凭证（type 不对 / 篡改） |
| 7003 | 账号或密码错误（不区分用户名/密码错） |
| 7004 | 管理员账号已被禁用 |
| 7005 | 管理员账号不存在 |
| 7006 | 密码长度不在 6-32 之间 |
| 7007 | 原密码不正确 |
| 7008 | 权限不足（VIEWER 写操作） |
| 7101 | 反馈记录不存在 |
| 7102 | 反馈状态值不合法 |
| 7201 | 目标用户不存在 |

---

## 7. 默认账号 & 初始化

应用首次启动时由 [`AdminDataInitializer`](../src/main/java/com/lanprojects/fitcoach/admin/config/AdminDataInitializer.java) 自动创建：

| 字段 | 值 |
|------|----|
| username | `admin` |
| password | `admin123` |
| role | `SUPER_ADMIN` |
| displayName | `超级管理员` |

> ⚠️ **生产环境务必首次登录后立即改密**，日志会以 `WARN` 级别打印创建提示。

后续新增管理员暂未提供 API（避免误开自助注册口子），可通过 SQL 直接插：

```sql
-- BCrypt 哈希可用 BCryptPasswordEncoder.encode("xxx") 单测生成
INSERT INTO admin_user(username, password_hash, display_name, role, enabled, created_at, updated_at)
VALUES ('viewer1', '$2a$10$...', '只读账号', 'VIEWER', true, NOW(), NOW());
```

---

## 8. 关键文件清单

```
fitcoach-admin/
├── pom.xml                              # 仅依赖 common/login/feedback + spring-security-crypto + jjwt
├── doc/
│   └── admin技术文档.md                 # 本文档
└── src/main/java/com/lanprojects/fitcoach/admin/
    ├── config/
    │   └── AdminDataInitializer        # 默认管理员账号初始化
    ├── controller/
    │   ├── AdminAuthController         # /api/admin/auth/*
    │   ├── AdminUserController         # /api/admin/users/*
    │   ├── AdminFeedbackController     # /api/admin/feedbacks/*
    │   └── DashboardController         # /api/admin/dashboard/*
    ├── dto/                            # 11 个请求/响应 DTO
    ├── entity/
    │   ├── AdminRole                   # SUPER_ADMIN / ADMIN / VIEWER + canWrite()
    │   └── AdminUser                   # 管理员账号实体
    ├── repository/
    │   └── AdminUserRepository         # findByUsername / existsByUsername
    ├── security/
    │   ├── AdminAuthInterceptor        # 统一鉴权拦截器
    │   ├── AdminWebMvcConfig           # 注册拦截器 + 路径配置
    │   └── AdminCryptoConfig           # BCryptPasswordEncoder Bean
    ├── service/
    │   ├── AdminAuthService            # 登录/改密/资料
    │   ├── AdminUserService            # 用户列表/详情/启禁
    │   ├── AdminFeedbackService        # 反馈列表/详情/状态流转
    │   ├── DashboardService            # 概览聚合
    │   └── AdminUrlService             # URL 拼接（保留相对路径，由前端拼绝对）
    └── util/
        └── AdminJwtUtils                # 独立 admin token（type=admin_access）
```

---

## 9. 与 AdminManager 前端的协议约定

- **baseURL**：前端通过 `VITE_API_BASE_URL` 环境变量配置 server 地址，所有请求拼接此前缀
- **图片 URL 拼接**：server 返回的 `avatarUrl` 可能是 `/static/avatar/xx.jpg` 这类相对路径，
  前端必须用 `baseURL + 相对路径` 拼接成绝对 URL（详见 [`AdminUrlService`](../src/main/java/com/lanprojects/fitcoach/admin/service/AdminUrlService.java) 的注释）
- **Token 持久化**：建议前端用 localStorage 存 token + role + displayName，路由层守卫读取并校验
- **错误处理**：HTTP 401 / business 7001 → 跳登录页；7008 → 提示权限不足

---

## 10. 扩展指南

### 新增管理员（后续做超管管理界面）

1. 复用 `AdminUserRepository`；新建 `AdminUserAdminService`（拗口但意思就是"管理员管理员"）
2. 接口 `POST /api/admin/admins`、`PUT /api/admin/admins/{id}`，仅 SUPER_ADMIN 可访问
3. 拦截器加细粒度角色校验：用 attribute 里写入的 role 在 controller 入口判断

### 新增订单管理（业务上线后）

1. 业务模块 `fitcoach-order` 落地 Order / OrderItem 实体
2. 在 `fitcoach-admin` 加 `AdminOrderController + AdminOrderService`，复用现有分页 + DTO 模式
3. 错误码段 `7301-7399` 给 order；ResultCode 文件预留位置

### Dashboard 加图表

- 当前接口只返回总数，前端 ECharts 渲染时缺时序数据
- 后续加 `/dashboard/users/trend?days=30`、`/dashboard/feedbacks/trend?days=30`，按天 group by

---

## 11. 安全审计清单

| 项 | 实现 |
|----|------|
| 密码不入日志 | `AdminAuthService.login` 失败统一报 `ADMIN_LOGIN_FAILED`，不区分账号/密码错 |
| 密码哈希 | BCrypt strength=10（[`AdminCryptoConfig`](../src/main/java/com/lanprojects/fitcoach/admin/security/AdminCryptoConfig.java)） |
| Token 隔离 | `type=admin_access` claim 强制隔离 user/admin token |
| 禁用立即生效 | 每次请求 `requireAdmin` 二次查 DB |
| 写操作权限 | VIEWER 一律拒（拦截器层） |
| 手机号脱敏 | 列表页脱敏，详情页才返回原值 |
| URL 注入 | 不在 server 拼绝对 URL，避免被反代 Host 头伪造 |

---

## 12. Git 仓库

| 项 | 值 |
|---|---|
| Server 仓库 | https://github.com/RicherLan/FitCoachServer.git |
| Admin 前端仓库 | git@github.com:RicherLan/FitCoachAdminManager.git |
