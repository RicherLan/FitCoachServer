# 📌 AI 接手须知（FitCoachServer）

> **本文是「协作契约」**：任何新接手 / 断线重连 / 切换 AI 工具的助手，**动手前必须先从头到尾读完**。
> 项目"是什么"请看 [`项目技术文档.md`](./项目技术文档.md)；本文只讲"和我协作的规矩"。

---

## 0. 四仓全景（务必先认清楚）

本工程不是孤岛，是 **4 个仓库协同开发**，本机路径都在 `~/code/lanprojects/`：

| 仓 | 路径 | 角色 |
|---|---|---|
| **FitCoachServer**（本仓） | `~/code/lanprojects/FitCoachServer` | Spring Boot 后端（10+ Maven 模块，Docker 单机部署） |
| FitCoachRN | `~/code/lanprojects/FitCoachRN` | RN 客户端（Android 已实装） |
| FitCoachAdminManager | `~/code/lanprojects/FitCoachAdminManager` | 管理后台（React + Vite + AntD） |
| fitcoach-website | `~/code/lanprojects/fitcoach-website` | 官网静态站（纯 HTML/CSS，部署在本 server 同台 CVM 的 nginx 容器，发布脚本就在本仓 [`shell/deploy-website.sh`](../shell/deploy-website.sh)）+ 品牌 VI 单一来源 [`BRAND.md`](../../fitcoach-website/BRAND.md) |

跨仓改动**必须多仓同步提**（举例：加一个 API → server 加 controller + RN 加 api 调用 + admin 加管理页 → 三仓各 commit + push；改公司信息 / 品牌 → 四仓同步）。

---

## 1. ⚠️ 铁律：改完即 commit + push（最重要！）

> **这一条违反一次都不行**。我（用户）以前的 AI 协作流程是「每完成一个主题就立刻 commit + push」，曾经因为 AI 连续干了 60+ 文件不提交，导致**生产服务器跑的还是老代码**，**绝对不要再犯**。

### 强制流程

1. **完成一个独立主题就立刻提交**（"独立主题"= 一个 todo / 一个 bug fix / 一个小功能）。**不要积压**。
2. 提交前必跑：`./mvnw compile -q` 或 `./mvnw -pl <模块> compile -q -am`，必须无 BUILD FAILURE。
3. 提交后**必须** `git push origin main`，不要只 commit 不 push。
4. 提交完用一句话告诉我：**「已 commit + push，hash: abc1234」**，方便我去服务器 `git pull && bash shell/deploy.sh`。

### Commit message 规范

格式：`<type>(<scope>): <中文描述>`

- `type`：`feat` / `fix` / `refactor` / `chore` / `docs` / `perf` / `style` / `test`
- `scope`：Maven 模块名去掉前缀，如 `training-record` / `membership` / `payment` / `admin` / `notification` / `track` / `app` / `infra`
- 描述：**中文**，一句话讲清楚做了什么

参考样例（取自最近真实历史）：

```
feat(notification): 加 platforms / minVersionCode / maxVersionCode 三段过滤
fix(infra): Dockerfile 补 fitcoach-notification / fitcoach-track 两个 module 的 COPY
feat(training-record): exercise 行加 iconUrl 快照
chore(seeder): 全量 86 个动作 emoji 重排
```

### 不要做的事

- ❌ 不要 `git commit -am` 一把梭把不相关的改动揉一起
- ❌ 不要写"WIP / temp / test"
- ❌ 不要忘 `git push`
- ❌ 不要为了"凑大 commit"故意延迟提交

---

## 2. ⚠️ 铁律：新增 Maven module 必同步改 Dockerfile（血泪教训）

[`Dockerfile`](../Dockerfile) 采用**白名单式逐模块 COPY**（依赖层 + 源码层各一份），目的是最大化利用 Docker 层缓存。**每次新增 module 必须同步改两处**，否则线上 `bash shell/deploy.sh` 会直接报：

```
Child module /build/<new-module> of /build/pom.xml does not exist
```

### 操作步骤（缺一不可）

1. 根 [`pom.xml`](../pom.xml) `<modules>` 块加新模块
2. [`Dockerfile`](../Dockerfile) **依赖层**（`# 4) 复制各模块 pom`）加：
   ```dockerfile
   COPY <new-module>/pom.xml <new-module>/
   ```
3. [`Dockerfile`](../Dockerfile) **源码层**（`# 6) 复制各模块源码`）加：
   ```dockerfile
   COPY <new-module> <new-module>
   ```
4. 本地 `./mvnw clean package -DskipTests -q` 至少跑一次，确认能打出 jar
5. 跟我说"已加模块 X，Dockerfile 两处 COPY 已同步"

> Dockerfile 顶部已有警告注释（commit `fca334b`），改之前先读那段注释。

---

## 3. ⚠️ 铁律：动手前先读 doc

> 本仓 `doc/` 和各 Maven 模块 `fitcoach-<xxx>/doc/` 都有专项技术文档。**不读 doc 就动手 = 一定会破坏既有约定或漏改字段。**

### 按场景必读清单

| 你要做的事 | 必读 doc |
|---|---|
| 看项目全貌（架构 / 模块拆分 / 通用约定） | [`项目技术文档.md`](./项目技术文档.md) |
| 部署 / 运维 / Docker / nginx / HTTPS | [`DEPLOY.md`](./DEPLOY.md) |
| 改会员 / 套餐 / 微信支付 / Apple IAP | [`会员支付集成.md`](./会员支付集成.md) |
| 改远程日志拉取 / debug 端点 | [`客户端日志远程拉取-集成与排错指南.md`](./客户端日志远程拉取-集成与排错指南.md) |
| 改具体模块（如 admin / training-record / notification） | `fitcoach-<模块>/doc/<模块>技术文档.md`（若有） |

---

## 4. ⚠️ 铁律：数据库变更必须给完整 SQL

任何 entity 加字段 / 改约束 / 加索引，**除了代码改动外**，必须额外给我：

```sql
-- 1. ALTER TABLE 增量 SQL（兼容已上线数据）
ALTER TABLE xxx ADD COLUMN yyy ...;
ALTER TABLE xxx ADD INDEX idx_yyy(yyy);

-- 2. Docker exec 一键执行命令
docker exec -i fitcoach-mysql-prod mysql -uroot -p'<pwd>' fitcoach < /tmp/xxx.sql
# 或
docker exec -it fitcoach-mysql-prod mysql -uroot -p'<pwd>' fitcoach -e "ALTER TABLE ..."
```

> 不要只靠 JPA `ddl-auto=update` —— 生产 `ddl-auto=none`，靠手动 SQL 升级。

---

## 5. 标准工作流

```
1. 读用户需求 → 拆 todo（update_todo_list）
2. 读相关模块 doc 和源码
3. 必要时跨仓读 RN / admin（路径见 § 0）
4. 改代码（apply_diff 优先，避免整文件重写）
5. ./mvnw -pl <模块> compile -q -am 验证 → 无 BUILD FAILURE
6. 涉及 DB 字段变更 → 准备 ALTER TABLE SQL
7. 涉及新 Maven module → 同步改 Dockerfile 两处 COPY
8. git add → commit（中文 message） → push
9. 告诉用户「已 commit + push, hash: xxxxxxx」+ 附带 SQL（如有）
10. 进入下一个 todo
```

---

## 6. 禁止行为清单

| ❌ 不要 | ✅ 应该 |
|---|---|
| 主动创建 `IMPLEMENTATION_COMPLETE.md` / `PHASE*_SUMMARY.md` 这种总结报告 | 进度直接在对话讲，需要长期文档时**问我**再写 |
| 用 `cat` / `echo` / `sed` 操作文件 | 用专用工具（read_file / apply_diff / write_to_file） |
| 直接改 entity 不给 ALTER SQL | 改 entity 必同步给 SQL |
| 加 Maven module 不改 Dockerfile | 必须同步改 Dockerfile 两处 COPY |
| controller 直接返回 entity | 必须走 DTO 转换 |
| 业务异常用 `throw new RuntimeException` | 用 `BusinessException(ResultCode.XXX)` |
| 金额用 `double` / `float` | 一律 `long` / `Integer` 存"分" |
| 密码 / token / appsecret 写代码里 | 一律走 `fitcoach-common/sysconfig`（加密配置） |
| 改完不验证 mvn compile 就 commit | mvn 必过再提交 |

---

## 7. 跨仓协同约定

| 场景 | 三仓动作 |
|---|---|
| 加一个客户端调用的接口 | server 加 controller + DTO → RN 加 `src/common/api/xxxApi.ts` → 三仓各 commit + push |
| 加一个管理后台用的接口 | server 加 admin controller → admin 加 api + 页面 → 双仓各 commit + push |
| 加一个新的 DB 字段 | server 改 entity + DTO + mvn → admin 改 type + 页面 → RN 改 model + 渲染 → **同时给 ALTER TABLE SQL** |
| 加一个新的 Maven module | server 改根 `pom.xml` + **同步改 Dockerfile 两处 COPY** |
| 改返回结构 | 三仓同步：server DTO → admin types → RN model → 各仓 tsc/mvn 验证 → 三仓 commit + push |
| 改公司信息 / API 域名 / 备案号 | server `application-prod.yml` + RN `httpClient` + admin `vite.config.ts` proxy + website 全站 + [`BRAND.md`](../../fitcoach-website/BRAND.md) → **四仓同步** |

---

## 8. 用户偏好（蓝伟华 / 林逸）

- 中文回复，简体中文，不要用 emoji 表情
- 直接出方案，不要反复确认"你确定吗"
- 出错了**承认错误**，不要狡辩 / 找借口
- 给命令优先给「我能直接复制粘贴跑」的完整命令，不要伪代码
- 生产 DB 变更必给完整 SQL + Docker exec 一键命令
- mvn compile / 测试结果**贴最后几行**给我看

---

## 9. 紧急情况

如果你（AI）发现：
- 本地有大量未提交的改动 → **立刻告诉我**，不要继续干新活
- 上一个会话的 commit 漏 push → **立刻 push**，告诉我 hash
- 自己写代码触发了线上 bug → **立刻 revert + push**，再告诉我原因
- Dockerfile 缺 COPY 导致部署失败 → **立刻补 + push**，并在 Dockerfile 顶部注释里再强化警告

---

**Last updated**: 2025-11 by 蓝伟华 — 这份文档是血泪教训，不允许打折执行。
