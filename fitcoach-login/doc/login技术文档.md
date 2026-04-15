# FitCoach Server — 登录模块技术文档

## 1. 模块概述

登录模块（fitcoach-login）负责用户认证，当前支持微信登录，架构上预留了手机号、邮箱等扩展。

### 核心职责

- 接收客户端微信授权码（code）
- 调用微信 API 换取 access_token 和用户信息
- 创建/更新本地用户记录
- 生成 JWT token 返回给客户端

---

## 2. 微信登录流程

```
Android 客户端                    后端 AuthController                微信服务器
     │                                │                                │
     │  POST /api/auth/wechat/login   │                                │
     │  { "code": "xxx" }             │                                │
     │──────────────────────────────> │                                │
     │                                │                                │
     │                                │  GET /sns/oauth2/access_token  │
     │                                │  ?appid=X&secret=X&code=xxx   │
     │                                │──────────────────────────────>│
     │                                │                                │
     │                                │  { access_token, openid }     │
     │                                │<──────────────────────────────│
     │                                │                                │
     │                                │  GET /sns/userinfo             │
     │                                │  ?access_token=X&openid=X     │
     │                                │──────────────────────────────>│
     │                                │                                │
     │                                │  { nickname, headimgurl, sex } │
     │                                │<──────────────────────────────│
     │                                │                                │
     │                                │  查找/创建 User                │
     │                                │  生成 JWT token                │
     │                                │                                │
     │  { user, token, expiresIn }    │                                │
     │<──────────────────────────────│                                │
```

---

## 3. 关键文件

| 文件 | 职责 |
|------|------|
| AuthController | REST 接口：`/api/auth/wechat/login`、`/api/auth/me` |
| AuthService | 登录流程协调：code→token→userInfo→创建用户→JWT |
| WeChatService | 微信 API 调用封装，AppId/AppSecret 从数据库读取 |
| User | 用户实体，支持 WECHAT/PHONE/EMAIL/GUEST 登录类型 |
| UserRepository | 用户数据访问，支持 openId/unionId/uid 查询 |
| JwtUtils | JWT 生成与解析工具 |
| LoginResponse | 登录成功返回的 DTO |

---

## 4. 微信配置

微信 AppId 和 AppSecret 存储在 `sys_config` 表中，通过 `SysConfigService` 读取：

| 配置键 | 说明 | 如何设置 |
|--------|------|---------|
| `wechat.app_id` | 微信开放平台 AppID | 后台管理平台设置，或直接修改数据库 |
| `wechat.app_secret` | 微信开放平台 AppSecret | 后台管理平台设置，或直接修改数据库 |

设置方式（开发阶段直接 SQL）：
```sql
UPDATE sys_config SET config_value = 'your_app_id' WHERE config_key = 'wechat.app_id';
UPDATE sys_config SET config_value = 'your_app_secret' WHERE config_key = 'wechat.app_secret';
```

---

## 5. JWT 认证

### 配置

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `jwt.secret` | FitCoach2026SecretKeyForJwtToken!! | 签名密钥（生产环境必须修改） |
| `jwt.expire_hours` | 168（7天） | Token 过期时间 |

### 客户端使用

```
请求头: Authorization: Bearer eyJhbGciOi...
```

### Token 内容

| 字段 | 说明 |
|------|------|
| sub | 用户 uid |
| iat | 签发时间 |
| exp | 过期时间 |

---

## 6. 扩展指南

### 新增登录方式（如手机号登录）

1. **DTO**：创建 `PhoneLoginRequest`（手机号 + 验证码）
2. **Service**：创建 `SmsService`（发送/验证短信验证码）
3. **AuthService**：新增 `phoneLogin()` 方法
4. **AuthController**：新增 `POST /api/auth/phone/login` 接口
5. **User**：`LoginType` 枚举已预留 `PHONE` 值

### 新增接口鉴权（后续）

可实现 `HandlerInterceptor` 或 Spring Security 的 `OncePerRequestFilter`，
在拦截器中调用 `JwtUtils.parseToken()` 验证请求头中的 token。
