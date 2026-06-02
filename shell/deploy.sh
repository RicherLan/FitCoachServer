#!/bin/bash
# ============================================================
# FitCoach Server — 生产部署脚本
#
# 在服务器上执行（不是本地！）：
#   cd /opt/fitcoach/FitCoachServer
#   bash shell/deploy.sh                  # 默认流程：拉代码 + build 镜像 + 重启
#   bash shell/deploy.sh --skip-build     # 跳过构建，直接用现有镜像重启
#   bash shell/deploy.sh --no-rollback    # 失败时不自动回滚（首次部署/调试期推荐）
#   bash shell/deploy.sh --rollback       # 回滚到上一个镜像 tag
#   bash shell/deploy.sh --logs           # 跟踪应用日志
#   bash shell/deploy.sh --status         # 查看容器状态
#
# 流程：
#   1. git pull 拉最新代码
#   2. docker compose build app 构建新镜像（打 tag :timestamp 备份）
#   3. docker compose up -d 滚动重启（mysql 不重启，只重启 app + nginx）
#   4. 健康检查（轮询 /api/auth/ping），失败自动回滚到上一版本
#   5. 成功后打印访问地址 + 镜像版本
# ============================================================

set -uo pipefail

# 颜色
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

# 路径
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# 配置
COMPOSE_FILE="shell/docker-compose.prod.yml"
ENV_FILE=".env.prod"
APP_IMAGE_NAME="fitcoach-server"
HEALTH_URL="http://localhost:${NGINX_HTTP_PORT:-80}/nginx-health"
APP_HEALTH_URL="http://localhost:${NGINX_HTTP_PORT:-80}/api/auth/ping"
HEALTH_TIMEOUT=120

# 参数
ACTION="deploy"
SKIP_BUILD=false
NO_ROLLBACK=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-build) SKIP_BUILD=true; shift ;;
        --no-rollback) NO_ROLLBACK=true; shift ;;
        --rollback)   ACTION="rollback"; shift ;;
        --logs)       ACTION="logs"; shift ;;
        --status)     ACTION="status"; shift ;;
        --stop)       ACTION="stop"; shift ;;
        -h|--help)
            sed -n '2,24p' "$0"
            exit 0
            ;;
        *) echo -e "${RED}❌ 未知参数: $1${NC}"; exit 1 ;;
    esac
done

# ====== 检查 ======
check_prerequisites() {
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}❌ Docker 未安装${NC}"; exit 1
    fi
    if ! docker info &> /dev/null; then
        echo -e "${RED}❌ Docker 未运行（请检查权限：sudo usermod -aG docker $USER）${NC}"; exit 1
    fi
    if docker compose version &> /dev/null; then
        COMPOSE_CMD="docker compose"
    elif command -v docker-compose &> /dev/null; then
        COMPOSE_CMD="docker-compose"
    else
        echo -e "${RED}❌ 未找到 docker compose${NC}"; exit 1
    fi
    if [ ! -f "$COMPOSE_FILE" ]; then
        echo -e "${RED}❌ 找不到 $COMPOSE_FILE${NC}"; exit 1
    fi
    if [ ! -f "$ENV_FILE" ]; then
        echo -e "${RED}❌ 找不到 $ENV_FILE${NC}"
        echo -e "${YELLOW}   请执行: cp .env.prod.example .env.prod 并编辑${NC}"
        exit 1
    fi
}

# ====== 子命令 ======

# 跟踪日志
do_logs() {
    $COMPOSE_CMD --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs -f --tail=200 app
}

# 状态
do_status() {
    $COMPOSE_CMD --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
    echo ""
    echo -e "${BLUE}镜像列表（最近 5 个）：${NC}"
    docker images "$APP_IMAGE_NAME" --format "table {{.Tag}}\t{{.CreatedSince}}\t{{.Size}}" | head -n 6
}

# 停止
do_stop() {
    echo -e "${YELLOW}停止所有服务...${NC}"
    $COMPOSE_CMD --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down
    echo -e "${GREEN}✅ 已停止${NC}"
}

# 回滚：找上一个镜像 tag，重新打 :latest
do_rollback() {
    echo -e "${YELLOW}查找可回滚的镜像...${NC}"
    # 按时间倒序，取第 2 个（第 1 个是当前 latest 指向的）
    LAST_TAG=$(docker images "$APP_IMAGE_NAME" --format "{{.Tag}}" \
        | grep -v "latest" | grep -v "<none>" \
        | sort -r | head -n 1)
    if [ -z "$LAST_TAG" ]; then
        echo -e "${RED}❌ 找不到可回滚的历史镜像${NC}"; exit 1
    fi
    echo -e "${YELLOW}将回滚到: ${APP_IMAGE_NAME}:${LAST_TAG}${NC}"
    docker tag "${APP_IMAGE_NAME}:${LAST_TAG}" "${APP_IMAGE_NAME}:latest"
    echo -e "${YELLOW}重启 app + nginx...${NC}"
    $COMPOSE_CMD --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d app nginx
    wait_for_health || { echo -e "${RED}❌ 回滚后仍不健康，需要人工介入${NC}"; exit 1; }
    echo -e "${GREEN}✅ 已回滚到 ${LAST_TAG}${NC}"
}

# 健康检查
wait_for_health() {
    echo -e "${YELLOW}等待 app 就绪（最多 ${HEALTH_TIMEOUT}s）...${NC}"
    local waited=0
    while [ $waited -lt $HEALTH_TIMEOUT ]; do
        # 先看容器是否还活着
        if ! $COMPOSE_CMD --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps app | grep -q "Up\|running"; then
            echo -e "${RED}❌ app 容器已退出${NC}"
            $COMPOSE_CMD --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=50 app
            return 1
        fi
        # ping nginx → app 全链路通才算 OK
        if curl -fsS --max-time 3 "$APP_HEALTH_URL" &> /dev/null; then
            echo -e "${GREEN}✅ 健康检查通过${NC}"
            return 0
        fi
        sleep 3
        waited=$((waited + 3))
        printf "."
    done
    echo ""
    echo -e "${RED}❌ 健康检查超时${NC}"
    $COMPOSE_CMD --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=50 app
    return 1
}

# 完整部署
do_deploy() {
    echo -e "${BLUE}=====================================================${NC}"
    echo -e "${BLUE}  FitCoach Server — 生产部署${NC}"
    echo -e "${BLUE}=====================================================${NC}"
    echo ""

    # Step 1: git pull
    echo -e "${YELLOW}[1/5] 拉取最新代码...${NC}"
    if [ -d ".git" ]; then
        git pull --rebase || { echo -e "${RED}❌ git pull 失败${NC}"; exit 1; }
        echo -e "${GREEN}✅ 已更新到: $(git --no-pager log -1 --oneline)${NC}"
    else
        echo -e "${YELLOW}⚠️  不是 git 仓库，跳过${NC}"
    fi
    echo ""

    # Step 2: 备份当前镜像（打时间戳 tag）
    echo -e "${YELLOW}[2/5] 备份当前镜像（用于回滚）...${NC}"
    if docker image inspect "${APP_IMAGE_NAME}:latest" &> /dev/null; then
        BACKUP_TAG=$(date +%Y%m%d-%H%M%S)
        docker tag "${APP_IMAGE_NAME}:latest" "${APP_IMAGE_NAME}:${BACKUP_TAG}"
        echo -e "${GREEN}✅ 已备份为: ${APP_IMAGE_NAME}:${BACKUP_TAG}${NC}"

        # 只保留最近 5 个备份，老的删除（避免磁盘塞满）
        OLD_TAGS=$(docker images "$APP_IMAGE_NAME" --format "{{.Tag}}" \
            | grep -E "^[0-9]{8}-[0-9]{6}$" | sort -r | tail -n +6)
        if [ -n "$OLD_TAGS" ]; then
            echo -e "${YELLOW}   清理老备份: $OLD_TAGS${NC}"
            echo "$OLD_TAGS" | xargs -I{} docker rmi "${APP_IMAGE_NAME}:{}" 2>/dev/null || true
        fi
    else
        echo -e "${YELLOW}⚠️  首次部署，无历史镜像可备份${NC}"
    fi
    echo ""

    # Step 3: 构建新镜像
    if [ "$SKIP_BUILD" = "true" ]; then
        echo -e "${YELLOW}[3/5] --skip-build 已指定，跳过构建${NC}"
    else
        echo -e "${YELLOW}[3/5] 构建新镜像（首次可能要 5-10 分钟）...${NC}"
        $COMPOSE_CMD --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build app || {
            echo -e "${RED}❌ 镜像构建失败${NC}"
            exit 1
        }
        echo -e "${GREEN}✅ 镜像构建完成${NC}"
    fi
    echo ""

    # Step 4: 滚动重启（mysql 不动，避免数据风险；只重启 app + nginx）
    echo -e "${YELLOW}[4/5] 启动 / 更新服务...${NC}"
    # 先确保 mysql 在跑
    $COMPOSE_CMD --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d mysql
    sleep 5
    # 再重启 app + nginx
    $COMPOSE_CMD --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d app nginx
    echo ""

    # Step 5: 健康检查 + 自动回滚
    echo -e "${YELLOW}[5/5] 健康检查...${NC}"
    if wait_for_health; then
        echo ""
        echo -e "${GREEN}=====================================================${NC}"
        echo -e "${GREEN}✅ 部署成功！${NC}"
        echo -e "${GREEN}   App 端口:    8080 (容器内)${NC}"
        echo -e "${GREEN}   Nginx 端口:  ${NGINX_HTTP_PORT:-80} / ${NGINX_HTTPS_PORT:-443}${NC}"
        echo -e "${GREEN}   健康检查:    curl $APP_HEALTH_URL${NC}"
        echo -e "${GREEN}   查看日志:    bash shell/deploy.sh --logs${NC}"
        echo -e "${GREEN}   查看状态:    bash shell/deploy.sh --status${NC}"
        echo -e "${GREEN}=====================================================${NC}"
    else
        echo ""
        echo -e "${RED}=====================================================${NC}"
        if [ "$NO_ROLLBACK" = "true" ]; then
            echo -e "${RED}❌ 健康检查失败（--no-rollback，不自动回滚）${NC}"
            echo -e "${YELLOW}   排查命令：bash shell/deploy.sh --logs${NC}"
            echo -e "${YELLOW}   手动回滚：bash shell/deploy.sh --rollback${NC}"
        else
            echo -e "${RED}❌ 健康检查失败，自动回滚...${NC}"
            do_rollback
        fi
        echo -e "${RED}=====================================================${NC}"
        exit 1
    fi
}

# ====== 主入口 ======
check_prerequisites
case "$ACTION" in
    deploy)   do_deploy ;;
    rollback) do_rollback ;;
    logs)     do_logs ;;
    status)   do_status ;;
    stop)     do_stop ;;
esac
