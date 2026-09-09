# TODO

> 项目待办总览，按模块记录。勾选式清单，做完一项勾一项。
> 相关文档：[`支付模块复用指南.md`](支付模块复用指南.md)。

---

## 一、支付 / 退款

### ✅ 已完成（波 0 → 波 5，三仓编译通过 + 已提交）

| 波 | 内容 | 提交 |
|---|---|---|
| 0 | payment 去业务化（plan → 通用 product 契约，可复用内核） | Server `0114514` / RN `51a1f71` / Admin `cc748cf` |
| 1 | 退款架构（ACTIVE/PASSIVE 两模式 + 独立 refund_order 表 + 事件解耦 + 自动撤会员） | Server + Admin `cb91a05` |
| 2 | 微信退款真实对接（`/v3/refund/domestic/refunds` + 退款回调） | Server |
| 3 | Apple IAP Server 骨架（验单 `/verify` + ASSN V2 退款通知） | Server |
| 4 | Google Play Server 骨架（验单 `/verify` + RTDN 退款通知） | Server |
| 5 | 摘除 ALIPAY 空实现 + 复用文档 | RN `e93fd46` / Server `8576613` |

### ⬜ 上线前必做（外部依赖，非代码缺陷）

**A. 微信支付（国内 Android）—— 代码已完整，只差配置**
- [ ] `sys_config` 配置 `payment.wechat.*`：`enabled / appId / mchId / apiV3Key / mchSerialNo / notifyUrl / refundNotifyUrl / mchPrivateKey / platformCertPem`
- [ ] `notifyUrl` = `https://migofitai.com/api/payment/notify/wechat`；`refundNotifyUrl` = `https://migofitai.com/api/payment/notify/wechat/refund`
- [ ] 两个回调 URL 在微信商户后台登记
- [ ] 端到端测试：支付→回调→开通会员；退款→回调→撤会员
- [ ] 确认生产 `payment.wechat.skipCallbackSignature=false`

**B. Apple IAP（iOS，含国内）—— Server 骨架完成，需补验签 + RN 端**
- [ ] 苹果开发者账号 + App Store Connect 配置 IAP 产品
- [ ] `sys_config` 配置 `payment.apple.*`：`enabled / bundleId / sharedSecret / sandbox`
- [ ] 【生产必做】补全 [`AppleIapService`](../fitcoach-payment/src/main/java/com/lanprojects/fitcoach/payment/provider/apple/AppleIapService.java) 的 JWS 证书链验签（当前仅解码未验签）
- [ ] ASSN V2 回调 URL 登记：`https://migofitai.com/api/payment/notify/apple`
- [ ] RN 端接 `react-native-iap`：StoreKit 购买 → signedTransaction → 调 `/api/payment/apple/verify`
- [ ] MembershipPlan 填 `applePriceTier` / `appleProductId`

**C. Google Play（海外 Android）—— Server 骨架完成，需补 API + RN 端**
- [ ] Google Play Console + Service Account（Developer API 权限）
- [ ] `sys_config` 配置 `payment.googleplay.*`：`enabled / packageName / serviceAccountJson`
- [ ] 【生产必做】补全 [`GooglePlayService`](../fitcoach-payment/src/main/java/com/lanprojects/fitcoach/payment/provider/google/GooglePlayService.java) 验单 + [`GooglePlayProvider`](../fitcoach-payment/src/main/java/com/lanprojects/fitcoach/payment/provider/google/GooglePlayProvider.java) 退款的 Developer API 真实调用
- [ ] RTDN 的 Cloud Pub/Sub topic 配置，回调 URL：`https://migofitai.com/api/payment/notify/google`
- [ ] RN 端接 Google Play Billing：purchaseToken → 调 `/api/payment/google/verify`
- [ ] MembershipPlan 填 `googleProductId` + `priceUsdCents`

**D. 通用 / 运维**
- [ ] 反代/安全配置确认放行（无 token）：`/api/payment/notify/**`
- [ ] 生产启动确认 `payment.mock.enabled=false`（[`PaymentProductionGuard`](../fitcoach-payment/src/main/java/com/lanprojects/fitcoach/payment/PaymentProductionGuard.java) 会 fail-fast）

### 🔮 可选增强（非阻塞）
- [ ] 退款结果对账 job（微信退款 PROCESSING 未收到回调时定时查询兜底）
- [ ] Admin 订单详情展示退款单历史（[`RefundService.listByOrderId`](../fitcoach-payment/src/main/java/com/lanprojects/fitcoach/payment/service/RefundService.java) 已就绪，缺接口 + UI）
- [ ] 部分退款按比例扣减会员天数（当前部分退款保留会员不处理）
- [ ] 自动续订（Apple/Google 订阅 + 微信委托代扣，`UserMembership.autoRenewEnabled` 预留）
- [ ] 错误码语义泛化：`MEMBERSHIP_PLAN_PRICE_INVALID` → 通用 `PAYMENT_PRODUCT_PRICE_INVALID`
- [ ] Web 端订阅（Stripe/微信 Web，作为 iOS 低抽成补充入口，绕开 IAP 抽成）
- [ ] 支付宝接入（枚举 `ALIPAY` 已预留，需 AlipayProvider + 加回白名单）

---

## 二、登录（Apple / Google 登录物理配置遗留）

> 代码已完成（阶段 3B），剩物理配置 + 真实 Client ID。

- [ ] Xcode → Sign In with Apple Capability
- [ ] Google Cloud Console 建 3 份 OAuth Client ID（Web / iOS / Android，Android 登记 SHA-1）
- [ ] `GoogleService-Info.plist` 拖入 Xcode target + Info URL Types 加 REVERSED_CLIENT_ID scheme
- [ ] `cd ios && pod install`
- [ ] RN `flavorProfile.ts` 替换 `googleSignIn` 的 `REPLACE_ME_*` 占位
- [ ] `sys_config`：`apple_login.client_ids` + `google_login.client_ids`

---

## 三、部署 / 运维

- [x] 域名 ICP 备案 + 服务器 HTTPS 配置（Let's Encrypt）
- [ ] 各端 baseURL / 回调 URL 上线前最终核对为 https
- [ ] 三仓改动 push 到远端

---

## 四、其他 / 技术债

- [ ] RN 存量 4 个 `track.*` TypeScript 报错（与支付无关，独立排查）
- [ ] 崩溃监控（Sentry/Bugsnag）、性能监控、CI/CD 打包自动化（上线前工程化收尾）
