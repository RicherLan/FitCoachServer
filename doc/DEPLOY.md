# FitCoach Server 生产部署指南

> 适用环境：**腾讯云 / 阿里云 / 任意 Linux 服务器**（推荐 Ubuntu 22.04+ / CentOS 8+，2C4G 起步）
>
> 全程基于 Docker，**单机部署**，无需 k8s。

---

## 目录

1. [架构总览](#1-架构总览)
2. [服务器初始化](#2-服务器初始化)
3. [首次部署](#3-首次部署)
4. [日常运维](#4-日常运维)
5. [HTTPS 证书配置](#5-https-证书配置)
6. [常见问题](#6-常见问题)
7. [回滚与应急](#7-回滚与应急)

---

## 1. 架构总览

```
┌──────────────────────────────────────────────────┐
│  腾讯云轻量服务器（Ubuntu 22.04，2C4G）            │
│                                                   │
│  ┌─ 公网入口 :80 / :443 ──────────────────────┐  │
│  │  ↓                                           │  │
│  │  ┌─────────────┐                            │  │
│  │  │   nginx     │ ← 容器：fitcoach-nginx-prod │  │
│  │  │  反代+SSL   │                            │  │
│  │  └──────┬──────┘                            │  │
│  │         ↓ 内网 :8080                         │  │
│  │  ┌─────────────┐                            │  │
│  │  │ Spring Boot │ ← 容器：fitcoach-app-prod   │  │
│  │  │  fitcoach   │                            │  │
│  │  └──────┬──────┘                            │  │
│  │         ↓ 内网 :3306                         │  │
│  │  ┌─────────────┐                            │  │
│  │  │  MySQL 8.0  │ ← 容器：fitcoach-mysql-prod │  │
│  │  └─────────────┘                            │  │
│  └────────────────────────────────────────────┘   │
│                                                   │
│  数据持久化（宿主机路径）：                         │
│    /data/fitcoach/mysql       MySQL 数据           │
│    /data/fitcoach/uploads     用户上传文件          │
│    /data/fitcoach/logs        应用日志              │
│    /data/fitcoach/certs       SSL 证书              │
└──────────────────────────────────────────────────┘
```

**关键设计**：
- **MySQL 不暴露公网端口**（只在 docker 内网），杜绝外网扫描爆破
- **App 不暴露公网端口**（只让 nginx 反代），所有流量经 nginx 限流/SSL
- **数据全部挂宿主机 volume**，容器随便删，数据不丢
- **镜像本地构建**，无需镜像仓库（小项目省成本）

---

## 2. 服务器初始化

### 2.1 系统准备（仅首次）

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装基础工具
sudo apt install -y git curl wget vim ufw
```

### 2.2 防火墙

```bash
# 只放行 SSH / HTTP / HTTPS，其他端口全关
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp     # SSH
sudo ufw allow 80/tcp     # HTTP
sudo ufw allow 443/tcp    # HTTPS
sudo ufw enable
sudo ufw status
```

⚠️ **腾讯云控制台还要在「安全组」里同步放行 80/443**，否则外网仍访问不通。

### 2.3 安装 Docker

```bash
# Docker 官方一键安装
curl -fsSL https://get.docker.com | bash

# 把当前用户加入 docker 组（避免每次 sudo）
sudo usermod -aG docker $USER
newgrp docker        # 立刻生效，不用重新登录

# 验证
docker --version
docker compose version
```

### 2.4 创建数据目录

```bash
sudo mkdir -p /data/fitcoach/{mysql,uploads,logs,certs}
sudo chown -R $USER:$USER /data/fitcoach
```

---

## 3. 首次部署

### 3.1 拉代码

```bash
# 建议放在 /opt 下，便于多人协作
sudo mkdir -p /opt && cd /opt
sudo chown $USER:$USER /opt

git clone git@github.com:RicherLan/FitCoachServer.git
cd FitCoachServer
```

> 💡 用 SSH key 而不是 HTTPS，避免每次 pull 都输密码。
> `ssh-keygen -t ed25519` 后把公钥加到 GitHub。

### 3.2 配置环境变量

```bash
cp .env.prod.example .env.prod
vim .env.prod
```

**必改项**（其他可暂时默认）：

| 变量 | 说明 | 示例 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | `openssl rand -base64 24` 生成 |
| `MYSQL_PASSWORD` / `DB_PASSWORD` | 业务库密码（两个保持一致） | 同上 |
| `CORS_ALLOWED_ORIGINS` | 前端域名 | `https://migofitai.com` |
| `SMS_PROVIDER` | 短信渠道 | `tencent`（生产）/ `mock`（不真发，仅测试） |
| `TENCENT_*` | 腾讯云短信 4 件套 | 从腾讯云 SMS 控制台获取 |
| `CAPTCHA_*` | 腾讯行为验证码 | 从验证码控制台获取 |

### 3.3 一键部署

```bash
bash shell/deploy.sh
```

脚本会自动：
1. git pull 拉最新代码
2. 备份当前镜像（首次跳过）
3. `docker compose build` 构建新镜像（**首次 5-10 分钟**，因为要下 Maven 依赖）
4. 启动 mysql → app → nginx
5. 健康检查 `/api/auth/ping`
6. 失败自动回滚

### 3.4 验证

```bash
# 1. 容器状态
bash shell/deploy.sh --status

# 2. 健康检查
curl https://migofitai.com/api/auth/ping
# 应返回: {"code":0,"message":"ok","data":"pong"}

# 3. nginx 健康
curl https://migofitai.com/nginx-health
# 应返回: ok

# 4. 应用日志
bash shell/deploy.sh --logs
```

### 3.5 首次必做

1. **登录 admin 后台改默认密码**
   - 访问 `https://migofitai.com/`（admin 静态资源就绪后）
   - 默认账号：`admin / admin123` —— **立即修改！**

2. **配置数据库自动备份**（见 [4.4](#44-数据库备份)）

3. **申请 SSL 证书启用 HTTPS**（见 [第 5 节](#5-https-证书配置)）

---

## 4. 日常运维

### 4.1 部署新版本

```bash
cd /opt/FitCoachServer
bash shell/deploy.sh
```

代码已 push 到 GitHub 后，服务器上一行命令完成更新。

### 4.2 查看日志

```bash
# 跟踪应用日志（实时）
bash shell/deploy.sh --logs

# 看历史日志（持久化在宿主机）
ls /data/fitcoach/logs/
tail -f /data/fitcoach/logs/fitcoach.log
```

### 4.3 进容器排查

```bash
# 进 app 容器
docker exec -it fitcoach-app-prod bash

# 进 mysql 客户端
docker exec -it fitcoach-mysql-prod mysql -u fitcoach -p fitcoach
```

### 4.4 数据库备份

**手动备份**：
```bash
docker exec fitcoach-mysql-prod mysqldump \
    -u root -p"$MYSQL_ROOT_PASSWORD" \
    --single-transaction \
    fitcoach > /data/fitcoach/backup/fitcoach-$(date +%Y%m%d).sql
```

**定时备份（cron 每天凌晨 3 点）**：
```bash
crontab -e
# 加一行：
0 3 * * * docker exec fitcoach-mysql-prod sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction fitcoach' > /data/fitcoach/backup/fitcoach-$(date +\%Y\%m\%d).sql 2>&1
```

**保留策略**（自动清理 30 天前的备份）：
```bash
# 加到 cron
0 4 * * * find /data/fitcoach/backup -name "fitcoach-*.sql" -mtime +30 -delete
```

### 4.5 镜像清理

部署多次后会累积旧镜像，`deploy.sh` 已自动保留最近 5 个备份，老的会清理。手动彻底清理：
```bash
docker image prune -a    # 删所有未使用镜像（小心！会删 mysql/nginx 等基础镜像）
```

---

## 5. HTTPS 证书配置

强烈建议上线就配 HTTPS。**Let's Encrypt 免费，自动续期**。

### 5.1 域名解析

去腾讯云 DNSPod 控制台确认 A 记录已生效：
```
migofitai.com    →    1.14.174.249
```

验证：
```bash
dig +short migofitai.com
# 应返回 1.14.174.249
```

### 5.2 申请证书

```bash
# 1. 临时停 nginx（certbot standalone 模式要占 80 端口验证）
docker compose -f shell/docker-compose.prod.yml --env-file .env.prod stop nginx

# 2. 安装 certbot 并申请（交互时输入邮箱、同意条款即可）
sudo apt install -y certbot
sudo certbot certonly --standalone -d migofitai.com

# 3. 证书会生成在 /etc/letsencrypt/live/migofitai.com/
sudo ls /etc/letsencrypt/live/migofitai.com/
# fullchain.pem  privkey.pem  ...

# 4. 拷贝到 Docker 挂载的 certs 目录
sudo cp /etc/letsencrypt/live/migofitai.com/fullchain.pem /data/fitcoach/certs/
sudo cp /etc/letsencrypt/live/migofitai.com/privkey.pem /data/fitcoach/certs/
sudo chmod 644 /data/fitcoach/certs/*.pem
```

### 5.3 启用 HTTPS

`nginx/fitcoach.conf` 已预配好 HTTPS（HTTP 80 自动 301 跳转到 HTTPS 443）。

只需重新部署即可生效：
```bash
bash shell/deploy.sh
```

或只重启 nginx：
```bash
docker compose -f shell/docker-compose.prod.yml --env-file .env.prod start nginx
```

验证：
```bash
# HTTPS 应正常返回
curl https://migofitai.com/api/auth/ping

# HTTP 应返回 301 跳转
curl -I http://migofitai.com
# 应看到: HTTP/1.1 301 Moved Permanently
# Location: https://migofitai.com/
```

### 5.4 自动续期

Let's Encrypt 证书 90 天有效，配 cron 自动续（每月 1 号凌晨 2 点）：
```bash
crontab -e
# 加：
0 2 1 * * docker compose -f /opt/FitCoachServer/shell/docker-compose.prod.yml --env-file /opt/FitCoachServer/.env.prod stop nginx && sudo certbot renew --quiet && sudo cp /etc/letsencrypt/live/migofitai.com/fullchain.pem /data/fitcoach/certs/ && sudo cp /etc/letsencrypt/live/migofitai.com/privkey.pem /data/fitcoach/certs/ && docker compose -f /opt/FitCoachServer/shell/docker-compose.prod.yml --env-file /opt/FitCoachServer/.env.prod start nginx
```

---

## 6. 常见问题

### Q1: `deploy.sh` 报 "Cannot connect to the Docker daemon"

```bash
sudo systemctl start docker
sudo usermod -aG docker $USER
newgrp docker
```

### Q2: 健康检查超时

```bash
# 看 app 日志
bash shell/deploy.sh --logs

# 常见原因：
# - 数据库密码错（DB_PASSWORD 和 MYSQL_PASSWORD 不一致）
# - CORS_ALLOWED_ORIGINS 漏配（启动直接拒）
# - 端口冲突（先看 lsof -i :80）
```

### Q3: MySQL 数据迁移到新服务器

```bash
# 旧服务器：导出
docker exec fitcoach-mysql-prod mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction fitcoach > backup.sql

# 把 backup.sql + 整个 /data/fitcoach/uploads 拷到新服务器

# 新服务器：先按本文档部署到「启动 mysql」步骤
# 然后导入：
docker exec -i fitcoach-mysql-prod mysql -u root -p"$MYSQL_ROOT_PASSWORD" fitcoach < backup.sql
```

### Q4: 想本地测试 `Dockerfile`

```bash
# 在 Mac 本地：
cd /Users/richerlan/code/lanprojects/FitCoachServer
docker build -t fitcoach-server:test .

# 跑起来（连 mac 本地的 mysql）：
docker run --rm -p 8081:8080 \
    -e SPRING_PROFILES_ACTIVE=dev \
    -e DB_URL=jdbc:mysql://host.docker.internal:3306/fitcoach \
    -e DB_PASSWORD=fitcoach123 \
    fitcoach-server:test
```

### Q5: 内存不够（OOM）

```bash
# 编辑 .env.prod，调低 JVM heap
JAVA_OPTS=-Xms128m -Xmx512m -XX:+UseG1GC
# 重启
bash shell/deploy.sh --skip-build
```

如果 2C4G 还是不够，建议升级到 4C8G。

---

## 7. 回滚与应急

### 一键回滚到上一版本

```bash
bash shell/deploy.sh --rollback
```

脚本会自动找最近一个时间戳备份镜像（如 `fitcoach-server:20260203-090000`），重打 `:latest` tag 并重启。

### 紧急停服

```bash
bash shell/deploy.sh --stop
```

### 完全重置（**谨慎！会删容器但保留 volume**）

```bash
docker compose -f shell/docker-compose.prod.yml --env-file .env.prod down
# 重启
bash shell/deploy.sh
```

### 删除所有数据（**毁灭性！只在测试环境用**）

```bash
docker compose -f shell/docker-compose.prod.yml --env-file .env.prod down -v
sudo rm -rf /data/fitcoach/mysql/*
sudo rm -rf /data/fitcoach/uploads/*
```

---

## 附录 A：文件清单

| 文件 | 作用 |
|---|---|
| `Dockerfile` | 多阶段构建：Maven build + JRE runtime |
| `.dockerignore` | 排除构建上下文不需要的文件 |
| `.env.prod.example` | 生产环境变量模板（复制成 `.env.prod` 后编辑） |
| `shell/docker-compose.prod.yml` | 生产编排：mysql + app + nginx |
| `shell/deploy.sh` | 一键部署 + 回滚 + 日志 + 状态 |
| `nginx/fitcoach.conf` | nginx 反代 + 限流 + HTTPS |
| `doc/DEPLOY.md` | 本文档 |

## 附录 B：扩展路线

| 阶段 | 触发条件 | 升级方案 |
|---|---|---|
| 当前 | 单机 + docker compose | 适合用户 < 10w |
| 加 Redis | OTP/缓存有压力 | 在 compose 加一个 redis 服务 |
| 加 CDN | 静态资源访问慢 | 用腾讯云 CDN 回源到 `/static` |
| 加 OSS | 上传文件 > 100GB | 把 `upload.base-dir` 切到 OSS |
| 多机部署 | DAU > 50w | 上 k8s（建议直接用云厂商托管，如 TKE） |
