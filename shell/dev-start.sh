#!/bin/bash
# ============================================================
# FitCoach Server — 开发环境启动脚本 (macOS)
#
# 功能：
#   1. 检查 Docker 是否运行
#   2. 启动 MySQL 容器 (docker-compose)
#   3. 等待 MySQL 就绪
#   4. 启动 Spring Boot 应用 (dev profile)
#
# 使用方式：
#   cd FitCoachServer
#   bash shell/dev-start.sh
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录（脚本所在目录的上一级）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}  FitCoach Server — 开发环境启动${NC}"
echo -e "${BLUE}=====================================================${NC}"
echo ""

# ====== Step 1: 检查 Docker ======
echo -e "${YELLOW}[1/4] 检查 Docker...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ 未安装 Docker，请先安装 Docker Desktop for Mac${NC}"
    echo "   下载地址: https://www.docker.com/products/docker-desktop/"
    exit 1
fi

if ! docker info &> /dev/null; then
    echo -e "${RED}❌ Docker 未运行，请先启动 Docker Desktop${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker 已就绪${NC}"

# ====== Step 2: 检查 docker-compose ======
if command -v docker-compose &> /dev/null; then
    COMPOSE_CMD="docker-compose"
elif docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
else
    echo -e "${RED}❌ 未找到 docker-compose，请安装 Docker Compose${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 使用 ${COMPOSE_CMD}${NC}"

# ====== Step 3: 启动 MySQL ======
echo ""
echo -e "${YELLOW}[2/4] 启动 MySQL 容器...${NC}"
cd "$SCRIPT_DIR"
$COMPOSE_CMD up -d

echo ""
echo -e "${YELLOW}[3/4] 等待 MySQL 就绪...${NC}"
MAX_WAIT=60
WAITED=0
while ! docker exec fitcoach-mysql mysqladmin ping -h localhost -u root -proot123 --silent &> /dev/null; do
    if [ $WAITED -ge $MAX_WAIT ]; then
        echo -e "${RED}❌ MySQL 启动超时（${MAX_WAIT}秒），请检查 Docker 日志：${NC}"
        echo "   docker logs fitcoach-mysql"
        exit 1
    fi
    sleep 2
    WAITED=$((WAITED + 2))
    echo -e "   等待中... (${WAITED}s)"
done
echo -e "${GREEN}✅ MySQL 已就绪${NC}"
echo -e "   连接信息: localhost:3306 / fitcoach / fitcoach123"

# ====== Step 4: 启动 Spring Boot ======
echo ""
echo -e "${YELLOW}[4/4] 启动 Spring Boot 应用...${NC}"
echo -e "   Profile: dev"
echo -e "   端口: 8080"
echo ""

cd "$PROJECT_DIR"

# 检查 mvnw 是否存在
if [ -f "./mvnw" ]; then
    chmod +x ./mvnw
    ./mvnw spring-boot:run -pl fitcoach-app -Dspring-boot.run.profiles=dev
else
    # 使用系统 Maven
    if command -v mvn &> /dev/null; then
        mvn spring-boot:run -pl fitcoach-app -Dspring-boot.run.profiles=dev
    else
        echo -e "${RED}❌ 未找到 Maven，请安装 Maven 或运行 mvnw wrapper${NC}"
        echo "   安装: brew install maven"
        echo "   或生成 wrapper: mvn wrapper:wrapper"
        exit 1
    fi
fi
