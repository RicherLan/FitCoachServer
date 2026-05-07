#!/bin/bash
# ============================================================
# FitCoach Server — 停止脚本
#
# 工作流程：
#   1. 优先读 shell/run/server.pid，按 PID 杀进程（推荐路径）
#   2. PID 文件缺失/进程已死 → fallback：按端口（默认 8080）lsof 找进程兜底
#   3. 先 SIGTERM（优雅退出，给 Spring Boot ContextClosedEvent 跑钩子）
#      最多等 15s；超时再 SIGKILL（强杀）
#
# 退出码：
#   0 = 进程已停止（本次干掉 / 本来就没在跑都算成功）
#   1 = 强杀也没杀掉（罕见，通常是 zombie 进程或权限问题）
#
# 不依赖 docker-compose，纯进程管理。MySQL 容器请手动 docker compose down。
# ============================================================

set -u  # 未定义变量直接报错；不用 set -e，因为 kill/grep 找不到也算正常路径

# 颜色
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUN_DIR="$SCRIPT_DIR/run"
PID_FILE="$RUN_DIR/server.pid"
APP_PORT="${APP_PORT:-8080}"
GRACEFUL_TIMEOUT_SECONDS=15

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}  FitCoach Server — 停止${NC}"
echo -e "${BLUE}=====================================================${NC}"

# ---------- 工具函数 ----------

# 判断 PID 是否活着（kill -0 不真发信号，只检查存在性）
is_alive() {
    local pid="$1"
    [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

# 优雅停 + 强杀兜底
graceful_kill() {
    local pid="$1"
    echo -e "${YELLOW}→ 发送 SIGTERM (PID=$pid)，最多等 ${GRACEFUL_TIMEOUT_SECONDS}s...${NC}"
    kill "$pid" 2>/dev/null || true

    local waited=0
    while is_alive "$pid"; do
        if [[ $waited -ge $GRACEFUL_TIMEOUT_SECONDS ]]; then
            echo -e "${YELLOW}→ 优雅退出超时，发送 SIGKILL...${NC}"
            kill -9 "$pid" 2>/dev/null || true
            sleep 1
            if is_alive "$pid"; then
                echo -e "${RED}❌ 强杀失败，PID=$pid 仍存活，请手动检查${NC}"
                return 1
            fi
            echo -e "${GREEN}✅ 已强制杀死 PID=$pid${NC}"
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
        printf "."
    done
    echo ""
    echo -e "${GREEN}✅ 进程 PID=$pid 已优雅退出${NC}"
    return 0
}

# ---------- 主流程 ----------

KILLED_ANY=0

# Step 1: 按 PID 文件杀
if [[ -f "$PID_FILE" ]]; then
    PID="$(cat "$PID_FILE" 2>/dev/null | tr -d '[:space:]')"
    if is_alive "$PID"; then
        echo -e "${YELLOW}[1/2] 通过 PID 文件停止进程 (PID=$PID)${NC}"
        if graceful_kill "$PID"; then
            KILLED_ANY=1
        else
            exit 1
        fi
    else
        echo -e "${YELLOW}[1/2] PID 文件存在但进程已不在 (PID=$PID)，清理...${NC}"
    fi
    rm -f "$PID_FILE"
else
    echo -e "${YELLOW}[1/2] 未发现 PID 文件，跳过${NC}"
fi

# Step 2: 端口兜底（防止有人手动启过 / PID 文件丢了）
# 安全策略：仅杀命令行匹配 fitcoach-app 的进程，避免误伤端口 8080 上的其他服务
PROCESS_FINGERPRINT="fitcoach-app"
echo -e "${YELLOW}[2/2] 端口 ${APP_PORT} 兜底检查（仅匹配 ${PROCESS_FINGERPRINT}）...${NC}"
if command -v lsof &>/dev/null; then
    PORT_PIDS="$(lsof -ti :"$APP_PORT" 2>/dev/null || true)"
    if [[ -n "$PORT_PIDS" ]]; then
        for pid in $PORT_PIDS; do
            # 用 ps 取完整命令行，匹配 fitcoach-app 才动手
            CMDLINE="$(ps -p "$pid" -o command= 2>/dev/null || true)"
            if [[ "$CMDLINE" == *"$PROCESS_FINGERPRINT"* ]]; then
                echo -e "${YELLOW}→ 端口 ${APP_PORT} 被 PID=$pid (fitcoach-app) 占用，停止之...${NC}"
                if graceful_kill "$pid"; then
                    KILLED_ANY=1
                fi
            else
                echo -e "${YELLOW}⚠️  端口 ${APP_PORT} 被 PID=$pid 占用，但不是 fitcoach-app，已跳过${NC}"
                echo -e "${YELLOW}    命令行: ${CMDLINE}${NC}"
                echo -e "${YELLOW}    如需自由端口请手动 kill 或换 APP_PORT 环境变量${NC}"
            fi
        done
    else
        echo -e "${GREEN}✅ 端口 ${APP_PORT} 空闲${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  未安装 lsof，跳过端口兜底（macOS/Linux 一般自带）${NC}"
fi

if [[ $KILLED_ANY -eq 0 ]]; then
    echo ""
    echo -e "${GREEN}✅ 没有需要停止的进程${NC}"
else
    echo ""
    echo -e "${GREEN}✅ FitCoach Server 已停止${NC}"
fi
