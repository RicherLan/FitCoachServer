# FitCoach Server — 登录模块技术文档（fitcoach-login）

> 给登录后端开发用的完整接口 + 设计参考。
> 项目总览 / 跨模块协作见 [`../../doc/项目技术文档.md`](../../doc/项目技术文档.md)。

---

## 1. 模块定位

`fitcoach-login` 负责所有"和用户身份相关"的能力，是整个服务端最基础的依赖模块。

### 核心职责

| 域 | 实现 |
|----|----|
| 多种方式登录 | 微信 / 手机号 OTP / 手机号 + 密码 / 邮箱（预留） / Guest / Google / Apple |
| Token 体系 | accessToken（2h）+ refreshToken（7d）+ 单设备互踢 sid claim |
| 用户资料 | 昵称 / 性别 / 头像（multipart 上传） |
| 密码管理 | 设置密码 / 改密 / 改密二选一校验（旧密码 vs OTP） |
| OTP / Captcha | 腾讯短信 + 腾讯滑块行为验证 + 限频 |
| 用户活跃 | `lastLoginAt`、`lastActiveAt`（120s 周期 touch） |
| 单设备互踢 | `currentSessionId` + JWT `sid` claim |

### 当前未做（占位）

- 邮箱登录（DTO 已留位，无 controller）
- Google / Apple OAuth（LoginType 已留位）
- Refresh token 黑名单（依赖 Redis，logout 当前只占位）

---

## 2. 路由总览

### 2.1 鉴权 / 登录（`AuthController`，前缀 `/api/auth`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/wechat/login` | 微信登录（code → JWT） |
| POST | `/phone/sendCode` | 发送短信验证码（先腾讯滑块 → 再 OTP 频控） |
| POST | `/phone/login` | 手机号 + OTP 登录（新手机号自动注册） |
| POST | `/login/password` | 手机号 + 密码登录（**不自动注册**） |
| POST | `/refresh` | refreshToken 续 accessToken |
| POST | `/logout` | 占位（仅校验合法性，未做黑名单） |
| GET  | `/me` | 当前用户基本信息 |
| GET  | `/ping` | 健康检查 |

### 2.2 用户资料 / 安全（`UserController`，前缀 `/api/user`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET   | `/me` | 当前用户资料（等价 /auth/me） |
| PATCH | `/profile` | 改昵称 / 性别（其他字段都可选） |
| POST  | `/avatar` | 上传头像（multipart，字段名 `file`） |
| GET   | `/password/exists` | 当前用户是否已设置密码（UI 分支用） |
| POST  | `/password` | 设置 / 修改密码（合并接口） |

---

## 3. 数据模型（`user` 表）

> 详见 [`User.java`](../src/main/java/com/lanprojects/fitcoach/login/entity/User.java)

| 字段 | 类型 | 索引 | 说明 |
|------|------|------|------|
| `uid` | VARCHAR(64) | unique | 业务 UUID |
| `nickname` | VARCHAR(100) | - | 昵称 |
| `avatar_url` | VARCHAR(500) | - | 相对路径如 `/static/avatar/<uid>/x.jpg` |
| `login_type` | ENUM | - | WECHAT / PHONE / EMAIL / GUEST / GOOGLE / APPLE |
| `open_id` | VARCHAR(100) | `uk_open_id` unique | 第三方 OpenID |
| `union_id` | VARCHAR(100) | `uk_union_id` unique | 第三方 UnionID |
| `gender` | INT | - | 0 未知 / 1 男 / 2 女 |
| `phone` | VARCHAR(20) | `uk_phone` unique（允许多个 NULL） | 手机号 |
| `email` | VARCHAR(100) | - | 预留 |
| `password_hash` | VARCHAR(100) | - | BCrypt 60 字符；NULL = 未设密码 |
| `enabled` | BOOLEAN | - | 禁用立即生效 |
| `last_login_at` | DATETIME | - | 最近一次成功登录 |
| `last_active_at` | DATETIME | - | 最近一次客户端轮询（120s 周期） |
| `current_session_id` | VARCHAR(64) | - | 当前 sid（单设备互踢用） |
| `current_device_id` | VARCHAR(64) | - | 当前活跃设备 ID |
| `current_login_at` | DATETIME | - | 当前 session 发起时间 |

### 设计要点

- `phone` 上 unique 但允许多个 NULL → 微信用户未绑定手机号也能并存
- `password_hash` NULL 表示"尚未设置密码"，密码登录需先通过 OTP / 微信登录后在"账号安全"里设置
- 互踢三件套（`current_session_id` / `current_device_id` / `current_login_at`）**只在带真实 deviceId 的登录时写入**，admin / Postman 登录不参与互踢

---

## 4. 多登录方式架构

```
        ┌────────────┐
        │ Controller │
        └─────┬──────┘
              ▼
   ┌──────────────────────┐         ┌─────────────────────┐
   │   AuthService        │────────▶│   AuthSupport       │
   │   (wechat/phone)     │         │  (公用：findOrCreate │
   │                      │         │   + JWT签发 +        │
   │   PasswordService    │         │   rotateSessionForLogin)
   │   (login.password)   │         └─────────────────────┘
   └──────────────────────┘                  │
        │                                    ▼
        │   WeChatService    JwtUtils    UserRepository
        │   OtpService       (sid claim) (find/save User)
        │   CaptchaService
        ▼
   TencentSmsService  ──┐
   MockSmsService     ──┴─▶  SmsService 接口（按 sms.enabled 切换）
```

### 4.1 微信登录链路

```
客户端 (RN/Android)             AuthController            WeChatService            微信
   │   POST /wechat/login         │                          │                       │
   │   {code}                     │                          │                       │
   │ ───────────────────────────▶ │                          │                       │
   │                              │ AuthService.wechatLogin  │                       │
   │                              │                          │ /sns/oauth2/access_token
   │                              │                          │ ────────────────────▶ │
   │                              │                          │   {access_token,openid}│
   │                              │                          │ ◀──────────────────── │
   │                              │                          │ /sns/userinfo         │
   │                              │                          │ ────────────────────▶ │
   │                              │                          │   {nickname,...}      │
   │                              │                          │ ◀──────────────────── │
   │                              │ findOrCreate by openId   │                       │
   │                              │ rotateSessionForLogin    │                       │
   │                              │ JwtUtils.issue(uid, sid) │                       │
   │   {user, accessToken, refreshToken, expiresIn}          │                       │
   │ ◀─────────────────────────── │                          │                       │
```

### 4.2 手机号 OTP 登录

```
1) POST /phone/sendCode
   - CaptchaService.verify(ticket, randstr, ip)  腾讯滑块
   - OtpService.requestOtp(phone, ip)
        ├─ 60s 同号重发冷却
        ├─ 1h 单号 5 条上限
        ├─ 单 IP 10 分钟 20 条上限
        └─ SmsService.send(phone, code)  (TencentSms / Mock)

2) POST /phone/login
   - OtpService.verifyOtp(phone, code)
        ├─ code 错误次数 5 次锁定 5 分钟
        └─ verify 通过即作废 code
   - AuthService.phoneLogin(phone)
        ├─ findOrCreateByPhone（首次自动注册 loginType=PHONE）
        ├─ rotateSessionForLogin（带 deviceId 才翻新 sid）
        └─ 颁 accessToken/refreshToken
```

### 4.3 密码登录

```
POST /login/password  { phone, password }
   - PasswordService.login(phone, password)
        ├─ findByPhone（无 → 抛 7401 PASSWORD_LOGIN_FAILED）
        ├─ passwordHash == null → 7401（不暴露"未设密码"）
        ├─ BCrypt.matches → false 抛 7401
        ├─ enabled=false → 3002 USER_DISABLED
        └─ rotateSessionForLogin + 颁 token
```

**安全策略**：所有失败情况统一返回 `7401 PASSWORD_LOGIN_FAILED`，避免泄露"账号是否存在 / 是否设置过密码"。

---

## 5. JWT 设计

### 5.1 配置

| sys_config key | 默认值 | 说明 |
|----------------|--------|------|
| `jwt.secret` | `FitCoach2026SecretKeyForJwtToken!!` | 签名密钥（**生产必须改**） |
| `jwt.expire_hours` | `2` | accessToken 过期（小时） |
| `jwt.refresh_expire_days` | `7` | refreshToken 过期（天） |

### 5.2 token claim

| claim | 含义 |
|-------|------|
| `sub` | 用户 uid |
| `type` | `access` / `refresh` |
| `sid` | session 标识（带真实 deviceId 的登录才有；老 token 无此 claim） |
| `iat` | 签发时间 |
| `exp` | 过期时间 |

### 5.3 Token 校验流程

```
AuthService.getCurrentUser(token)
  → JwtUtils.parsePayload(token)        签名 + 过期 + type=access 校验
  → userRepository.findByUid(sub)       账号存在校验
  → assert user.enabled = true          禁用立即生效（3002 USER_DISABLED）
  → parseAndAssertSession(jwt.sid, user.currentSessionId)
        ├─ jwt.sid 为 null（老 token）→ 放行
        ├─ user.currentSessionId 为 null（未参与互踢）→ 放行
        └─ jwt.sid != user.currentSessionId → 抛 1006 SESSION_KICKED
```

### 5.4 refresh 流程

```
POST /api/auth/refresh  { refreshToken }
  → JwtUtils 校验 type=refresh
  → 同上 sid 校验（旧设备的 refreshToken 也会被互踢）
  → 颁新 accessToken（refreshToken 不滚，复用直到自然过期）
```

> 客户端 [`httpClient`](../../../FitCoachRN/src/common/http/httpClient.ts) 全局拦截 1001-1003 → 自动 refresh + 重放原请求；最多一次回避无限循环。

---

## 6. 单设备登录互踢（详）

### 6.1 数据 & 流程

```
设备 A 登录 (X-Device-Id=A)
    ↓ AuthService.wechatLogin / phoneLogin / PasswordService.login
    ↓ AuthSupport.rotateSessionForLogin(user)
        ├─ if !ClientContext.get().hasDeviceId() → 跳过（admin/Postman 不参与）
        ├─ sid_A = UUID()
        ├─ user.currentSessionId = sid_A
        ├─ user.currentDeviceId  = "A"
        └─ user.currentLoginAt   = now()
    ↓ JwtUtils.issueAccess(uid, sid_A) / issueRefresh(uid, sid_A)

设备 B 登录 (X-Device-Id=B)
    ↓ 同上 → user.currentSessionId = sid_B

设备 A 任意鉴权请求 (jwt.sid=sid_A)
    ↓ AuthService.getCurrentUser → parseAndAssertSession
    ↓ sid_A != user.currentSessionId(=sid_B)
    ↓ throw BusinessException(SESSION_KICKED)  // code=1006

RN httpClient 拦截 1006
    ↓ 调用 setSessionKickedHandler 注入的回调
    ↓ UserManager.logout() + ToastAndroid + navigationRef.navigate('Login')
```

### 6.2 边界

- 老 token（无 sid claim）：放行（向后兼容）；上线后所有新登录都会带 sid，老 token 7 天后自然失效
- admin / Postman / 启动早期：无 `X-Device-Id` 或值为 `unknown` → 不进 rotateSessionForLogin，user.currentSessionId 维持 null，互踢就是 no-op
- 同 deviceId 重登（手动 logout 再 login）：sid 仍翻新，旧 token 即刻失效；但此时只有该设备自己持有旧 token，自然就不会被踢

---

## 7. 头像上传

### 7.1 链路

```
POST /api/user/avatar (multipart/form-data, file=...)
    → UserController.uploadAvatar
    → UserProfileService.updateAvatar(uid, file)
        ├─ 校验 contentType ∈ {jpeg, png, webp}
        ├─ 校验 size ≤ 2MB（客户端应先压到 ~200KB）
        └─ AvatarStorageService.store(uid, file) → 返回相对路径
    → user.avatarUrl = "/static/avatar/<uid>/<uuid>.<ext>"
    → 返回更新后的 LoginResponse
```

### 7.2 存储抽象

```
AvatarStorageService  (interface)
    └── LocalAvatarStorageService  (当前唯一实现)
            落点：${upload.base-dir}/avatar/<uid>/<uuid>.<ext>

后续：加 OssAvatarStorageService 实现该接口、用 @ConditionalOnProperty 切换，业务无感。
```

### 7.3 客户端注意

- **必须本地压缩到 ~200KB（512x512 + quality 0.7）**，否则上传又慢又消耗服务端带宽
- server 只返相对路径；客户端用 `baseURL + relativePath` 拼绝对 URL 展示

---

## 8. OTP / Captcha / SMS

### 8.1 OtpService 限频

| 维度 | 限制 |
|------|------|
| 同手机号重发 | 60s 冷却 |
| 同手机号 1h | 5 条上限 |
| 同 IP 10min | 20 条上限 |
| code 校验失败 | 5 次锁定 5 分钟 |
| code 有效期 | 10min |

### 8.2 CaptchaService（腾讯滑块）

| sys_config key | 说明 |
|----------------|------|
| `captcha.enabled` | true/false 全局开关 |
| `captcha.app_id` | 腾讯滑块 AppID |
| `captcha.app_secret` | 腾讯滑块 AppSecret |

`captcha.enabled = false` 时自动跳过校验（dev/sit 调试用），生产必须打开。

### 8.3 SmsService 切换

```
sms.enabled = true   → TencentSmsService（腾讯云短信 V1.0 接口）
sms.enabled = false  → MockSmsService（日志打印验证码，dev/sit 用）
```

| sys_config key | 说明 |
|----------------|------|
| `sms.enabled` | true/false |
| `sms.secret_id` | 腾讯云 SecretId |
| `sms.secret_key` | 腾讯云 SecretKey |
| `sms.sign_name` | 短信签名 |
| `sms.template_id` | 短信模板 ID |
| `sms.sdk_app_id` | 短信 SDK AppID |

---

## 9. 密码服务（PasswordService）

### 9.1 设置 / 修改密码

```
POST /api/user/password
Body:
{
  "newPassword": "Abc123!",
  "oldPassword": "<可选>",
  "otpCode":     "<可选>"
}

业务分支：
  当前未设密码 → 必须提供 otpCode（先 POST /api/auth/phone/sendCode）
  当前已设密码 → oldPassword 与 otpCode 二选一
```

### 9.2 密码格式

- 长度 6-32
- 至少 1 字母 + 1 数字
- 不允许全数字 / 全字母

---

## 10. 用户活跃统计（UserActivityService）

```
GET /api/client/poll  (clientbus 模块统一轮询入口，120s 周期)
    → AuthService.getCurrentUser  (顺带触发 lastActiveAt 更新)
    → UserActivityService.touch(uid)
            ├─ 节流：1 分钟内不重复写库（内存 set 缓冲）
            └─ flush 时 update user.last_active_at = now()
```

**admin 判定在线**：`now - lastActiveAt < 5min` → 在线（120s 周期 × 2.5 倍冗余，避免一次轮询丢失就判离线）。

不在每个业务接口内调 `touch()`，避免散落；统一靠 `/api/client/poll` 一处覆盖。

---

## 11. 错误码（login 段）

> 完整列表见 [`ResultCode`](../../fitcoach-common/src/main/java/com/lanprojects/fitcoach/common/model/ResultCode.java)。

### 11.1 鉴权 / 用户（1001-1099 / 3001-3099）

| 码 | 含义 |
|----|------|
| 1001 | UNAUTHORIZED（缺 token） |
| 1002 | TOKEN_INVALID（签名/格式错） |
| 1003 | TOKEN_EXPIRED |
| 1004 | REFRESH_TOKEN_INVALID |
| 1005 | TOKEN_TYPE_MISMATCH |
| 1006 | SESSION_KICKED（单设备互踢） |
| 3001 | USER_NOT_FOUND |
| 3002 | USER_DISABLED（admin 禁用立即生效） |

### 11.2 登录 / 注册（7401-7499）

| 码 | 含义 |
|----|------|
| 7401 | PASSWORD_LOGIN_FAILED（密码登录所有失败统一码） |
| 7402 | PASSWORD_FORMAT_INVALID |
| 7403 | PASSWORD_NOT_SET_FOR_USER |
| 7404 | OLD_PASSWORD_MISMATCH |

### 11.3 OTP / Captcha / SMS（7411-7499）

| 码 | 含义 |
|----|------|
| 7411 | OTP_RATE_LIMIT_TOO_FAST（60s 冷却） |
| 7412 | OTP_RATE_LIMIT_PHONE_HOURLY |
| 7413 | OTP_RATE_LIMIT_IP |
| 7421 | OTP_NOT_FOUND_OR_EXPIRED |
| 7422 | OTP_VERIFY_TOO_MANY |
| 7423 | OTP_CODE_INVALID |
| 7431 | CAPTCHA_TICKET_REQUIRED |
| 7432 | CAPTCHA_VERIFY_FAILED |
| 7491 | SMS_SEND_FAILED |

> 实际码值以代码为准；本表用于让客户端 / 前端理解分段约定。

---

## 12. 关键文件

```
fitcoach-login/
├── pom.xml                # 依赖 common + jjwt + spring-security-crypto + hutool-http
├── doc/
│   └── login技术文档.md   # 本文档
└── src/main/java/com/lanprojects/fitcoach/login/
    ├── config/
    │   └── PasswordEncoderConfig    # BCryptPasswordEncoder Bean（admin 模块也复用）
    ├── controller/
    │   ├── AuthController            # /api/auth/*
    │   └── UserController            # /api/user/*
    ├── dto/
    │   ├── LoginResponse             # 三种登录都返回这个 DTO
    │   ├── WeChatLoginRequest / TokenResponse / UserInfo
    │   ├── PhoneLoginRequest
    │   ├── PasswordLoginRequest
    │   ├── SendCodeRequest           # 含腾讯滑块 ticket / randstr
    │   ├── SetPasswordRequest        # 设置/改密合并 DTO
    │   ├── UpdateProfileRequest
    │   └── RefreshTokenRequest
    ├── entity/
    │   └── User                      # 含 6 种 LoginType + 互踢三件套 + 活跃字段
    ├── repository/
    │   └── UserRepository            # findByUid/openId/unionId/phone/email + findByUidIn
    ├── service/
    │   ├── AuthService               # 微信 + 手机号登录入口
    │   ├── WeChatService             # 微信 API 调用
    │   ├── CaptchaService            # 腾讯滑块校验
    │   ├── OtpService                # OTP 限频 + 发送 + 校验
    │   ├── PasswordService           # 密码登录 + 设置/改密
    │   ├── UserProfileService        # 改昵称/性别/头像
    │   ├── UserActivityService       # lastActiveAt 节流更新
    │   ├── AvatarStorageService      # 头像存储抽象
    │   └── LocalAvatarStorageService # 本地实现（默认）
    ├── sms/
    │   ├── SmsService                # SMS 抽象
    │   ├── TencentSmsService         # 腾讯云短信
    │   ├── MockSmsService            # dev/sit 日志打印
    │   └── SmsException
    ├── support/
    │   └── AuthSupport               # 公用：findOrCreate + rotateSessionForLogin + 颁 token
    └── util/
        └── JwtUtils                  # parsePayload / issueAccess / issueRefresh
                                      # 含 sid claim
```

---

## 13. 扩展指南

### 13.1 新增登录方式（如 Google）

1. **DTO**：`GoogleLoginRequest`（idToken）
2. **Service**：`GoogleService.verifyIdToken(idToken)` → `{ sub, email, name, picture }`
3. **AuthService**：新增 `googleLogin(idToken)`，复用 `AuthSupport.findOrCreate*` + `rotateSessionForLogin`
4. **Controller**：`POST /api/auth/google/login`
5. **User.LoginType**：枚举已留 `GOOGLE`，无需 DDL

### 13.2 引入 Redis 后的优化

| 当前 | 引入 Redis 后 |
|------|--------------|
| OTP 写内存 ConcurrentHashMap | 改 Redis key（带 TTL）；分布式生效 |
| UserActivityService 单节点节流 | Redis SET（带 TTL）做全集群节流 |
| logout 仅占位 | refreshToken jti 加入黑名单，立即作废 |
| lastActiveAt 写库节流 | 用 Redis ZSet 写在线列表，admin 查询 O(logN) |

### 13.3 邮箱登录补齐

1. `User.email` 已存在；表加 unique 索引
2. `EmailService` 抽象 + `MockEmailService` / `AwsSesEmailService`
3. 复用 OtpService 的限频逻辑，改 `requestEmailOtp / verifyEmailOtp`
4. Controller `POST /api/auth/email/sendCode` + `POST /api/auth/email/login`

---

## 14. 相关文档

| 文档 | 说明 |
|------|------|
| [`../../doc/项目技术文档.md`](../../doc/项目技术文档.md) | 服务端总览 |
| [`../../doc/会员支付集成.md`](../../doc/会员支付集成.md) | 会员/支付协议 |
| [`../../fitcoach-admin/doc/admin技术文档.md`](../../fitcoach-admin/doc/admin技术文档.md) | 管理后台 API |
| [`../../../FitCoachRN/src/login/doc/login技术文档.md`](../../../FitCoachRN/src/login/doc/login技术文档.md) | 客户端登录模块 |
