#!/bin/bash
# ============================================================
# FitCoach Server — 编译 + 后台启动脚本
#
# 工作流程：
#   1. 调 stop.sh 先停（避免重复启动占端口）
#   2. mvn clean package 打 fat jar（默认跳过测试，加 --with-tests 不跳）
#   3. nohup java -jar 后台启动，PID 写到 shell/run/server.pid
#   4. 轮询 /api/auth/ping 直到 200，最多等 60s
#
# 使用方式：
#   bash shell/start.sh                # 默认 dev profile + 跳过测试
#   bash shell/start.sh --profile prod # 指定 profile
#   bash shell/start.sh --with-tests   # 编译时不跳过测试
#   bash shell/start.sh --skip-build   # 跳过编译，直接用上次的 jar 起
#
# 日志：
#   shell/run/server.out  应用 stdout/stderr（同时 logback 写 logs/）
#   shell/run/server.pid  当前运行的 PID
#
# 退出码：
#   0 = 启动并通过健康检查
#   1 = 编译失败 / 启动超时 / jar 启动后立即退出
# ============================================================

set -u

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

# ---------- 路径常量 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RUN_DIR="$SCRIPT_DIR/run"
PID_FILE="$RUN_DIR/server.pid"
LOG_FILE="$RUN_DIR/server.out"

APP_MODULE="fitcoach-app"
APP_JAR="$PROJECT_DIR/$APP_MODULE/target/$APP_MODULE-1.0.0-SNAPSHOT.jar"
APP_PORT="${APP_PORT:-8080}"
HEALTH_URL="http://localhost:${APP_PORT}/api/auth/ping"
HEALTH_TIMEOUT_SECONDS=60

# ---------- 参数解析 ----------
PROFILE="dev"
SKIP_TESTS=true
SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --profile)
            PROFILE="$2"
            shift 2
            ;;
        --with-tests)
            SKIP_TESTS=false
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        -h|--help)
            sed -n '2,28p' "$0"
            exit 0
            ;;
        *)
            echo -e "${RED}❌ 未知参数: $1${NC}"
            exit 1
            ;;
    esac
done

mkdir -p "$RUN_DIR"

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}  FitCoach Server — 编译 + 启动${NC}"
echo -e "${BLUE}  profile=${PROFILE} | port=${APP_PORT} | skipTests=${SKIP_TESTS}${NC}"
echo -e "${BLUE}=====================================================${NC}"
echo ""

# ---------- Step 1: 先停旧进程 ----------
echo -e "${YELLOW}[1/4] 先停旧进程...${NC}"
bash "$SCRIPT_DIR/stop.sh" || {
    echo -e "${RED}❌ 停止旧进程失败，请手动排查后重试${NC}"
    exit 1
}
echo ""

# ---------- Step 2: 编译 ----------
if [[ "$SKIP_BUILD" == "true" ]]; then
    echo -e "${YELLOW}[2/4] --skip-build 已指定，跳过编译${NC}"
    if [[ ! -f "$APP_JAR" ]]; then
        echo -e "${RED}❌ jar 不存在: $APP_JAR${NC}"
        echo -e "${RED}   请先去掉 --skip-build 跑一次完整编译${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}[2/4] mvn clean package 编译中（fat jar）...${NC}"
    cd "$PROJECT_DIR"

    if [[ -f "./mvnw" ]]; then
        chmod +x ./mvnw
        MVN_CMD="./mvnw"
    elif command -v mvn &>/dev/null; then
        MVN_CMD="mvn"
    else
        echo -e "${RED}❌ 未找到 mvn 或 ./mvnw${NC}"
        exit 1
    fi

    MVN_ARGS=("-q" "-pl" "$APP_MODULE" "-am" "clean" "package")
    if [[ "$SKIP_TESTS" == "true" ]]; then
        MVN_ARGS+=("-DskipTests")
    fi

    if ! "$MVN_CMD" "${MVN_ARGS[@]}"; then
        echo -e "${RED}❌ Maven 编译失败，详情见上方输出${NC}"
        exit 1
    fi

    if [[ ! -f "$APP_JAR" ]]; then
        echo -e "${RED}❌ 编译完成但找不到产物 jar: $APP_JAR${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ 编译成功: $APP_JAR${NC}"
fi
echo ""

# ---------- Step 3: 后台启动 ----------
echo -e "${YELLOW}[3/4] 后台启动应用...${NC}"

# JVM 参数（生产可通过环境变量覆盖）
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx1g -XX:+UseG1GC}"
SPRING_OPTS="--spring.profiles.active=${PROFILE} --server.port=${APP_PORT}"

# 清空旧日志，避免被误读成本次启动失败
: > "$LOG_FILE"

# nohup + & 后台跑；setsid 在 macOS 默认没有，用 nohup 就够
nohup java $JAVA_OPTS -jar "$APP_JAR" $SPRING_OPTS \
    >> "$LOG_FILE" 2>&1 &
APP_PID=$!

echo "$APP_PID" > "$PID_FILE"
echo -e "${GREEN}✅ 已拉起进程 PID=${APP_PID}${NC}"
echo -e "   日志: $LOG_FILE"
echo -e "   PID 文件: $PID_FILE"
echo ""

# ---------- Step 4: 健康检查 ----------
echo -e "${YELLOW}[4/4] 等待应用就绪（最多 ${HEALTH_TIMEOUT_SECONDS}s）...${NC}"
WAITED=0
while true; do
    # 进程意外退出（端口冲突 / 配置错 / OOM 等）→ 立刻 dump 日志尾部并退出
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        echo -e "${RED}❌ 进程 PID=${APP_PID} 已退出，启动失败${NC}"
        echo -e "${RED}--- 日志最后 50 行 ---${NC}"
        tail -n 50 "$LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi

    # ping 通即认为启动完成
    if command -v curl &>/dev/null; then
        if curl -fsS --max-time 2 "$HEALTH_URL" &>/dev/null; then
            echo ""
            echo -e "${GREEN}=====================================================${NC}"
            echo -e "${GREEN}✅ FitCoach Server 启动成功${NC}"
            echo -e "${GREEN}   PID:    ${APP_PID}${NC}"
            echo -e "${GREEN}   Port:   ${APP_PORT}${NC}"
            echo -e "${GREEN}   Health: ${HEALTH_URL}${NC}"
            echo -e "${GREEN}   Log:    ${LOG_FILE}${NC}"
            echo -e "${GREEN}                tail -f ${LOG_FILE}${NC}"
            echo -e "${GREEN}   Stop:   bash shell/stop.sh${NC}"
            echo -e "${GREEN}=====================================================${NC}"
            exit 0
        fi
    fi

    if [[ $WAITED -ge $HEALTH_TIMEOUT_SECONDS ]]; then
        echo -e "${RED}❌ 健康检查超时（${HEALTH_TIMEOUT_SECONDS}s），进程仍存活但未就绪${NC}"
        echo -e "${RED}   建议：tail -f ${LOG_FILE} 看启动卡在哪${NC}"
        echo -e "${RED}   或者 bash shell/stop.sh 手动停止${NC}"
        exit 1
    fi
    sleep 2
    WAITED=$((WAITED + 2))
    printf "."
done
