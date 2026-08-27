#!/bin/bash
# ============================================================
# MIGO FIT 官方网站（fitcoach-website）部署脚本
#
# 在服务器上执行（不是本地！）：
#   cd /opt/fitcoach/FitCoachServer
#   bash shell/deploy-website.sh init    # 首次部署：git clone 到 WEBSITE_DIR
#   bash shell/deploy-website.sh         # 后续更新：git pull + nginx reload
#   bash shell/deploy-website.sh status  # 看部署状态 + 最后一次 commit
#
# 设计说明：
#   - 官网仓库独立维护在 github.com/RicherLan/fitcoach-website
#   - 服务器上 git clone 到 /data/fitcoach/website（不污染 server 仓库）
#   - docker-compose 把它只读挂载到 nginx 容器 /usr/share/nginx/website
#   - 通过 docker exec nginx -s reload 实现零停机更新
# ============================================================

set -uo pipefail

# 颜色
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

# 路径
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# 加载 .env.prod（如果存在）以读取 WEBSITE_DIR
if [ -f ".env.prod" ]; then
    set -a
    # shellcheck disable=SC1091
    . ./.env.prod
    set +a
fi

# 配置（可被 .env.prod 覆盖）
WEBSITE_DIR="${WEBSITE_DIR:-/data/fitcoach/website}"
WEBSITE_REPO="${WEBSITE_REPO:-https://github.com/RicherLan/fitcoach-website.git}"
WEBSITE_BRANCH="${WEBSITE_BRANCH:-main}"
COMPOSE_FILE="shell/docker-compose.prod.yml"
NGINX_SERVICE="nginx"

log_info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()      { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }

# 检查 nginx 容器在运行
ensure_nginx_running() {
    if ! docker compose -f "$COMPOSE_FILE" ps --status running --services 2>/dev/null | grep -q "^${NGINX_SERVICE}$"; then
        log_warn "nginx 容器未运行，请先执行：bash shell/deploy.sh"
        return 1
    fi
    return 0
}

# nginx 配置语法校验
nginx_test_config() {
    log_info "校验 nginx 配置语法..."
    if docker compose -f "$COMPOSE_FILE" exec -T "$NGINX_SERVICE" nginx -t >/dev/null 2>&1; then
        log_ok "nginx 配置语法 OK"
        return 0
    else
        log_error "nginx 配置语法错误，已中止 reload。详细信息："
        docker compose -f "$COMPOSE_FILE" exec -T "$NGINX_SERVICE" nginx -t || true
        return 1
    fi
}

# nginx 零停机 reload
nginx_reload() {
    log_info "reload nginx（零停机）..."
    if docker compose -f "$COMPOSE_FILE" exec -T "$NGINX_SERVICE" nginx -s reload >/dev/null 2>&1; then
        log_ok "nginx reload 成功"
        return 0
    else
        log_error "nginx reload 失败"
        return 1
    fi
}

# 首次部署：git clone
cmd_init() {
    log_info "============================================================"
    log_info "首次部署 fitcoach-website"
    log_info "============================================================"
    log_info "目标目录：$WEBSITE_DIR"
    log_info "仓库地址：$WEBSITE_REPO"
    log_info "分支：    $WEBSITE_BRANCH"
    echo ""

    if [ -d "$WEBSITE_DIR/.git" ]; then
        log_warn "$WEBSITE_DIR 已是 git 仓库，跳过 clone。"
        log_warn "如需更新代码请直接运行：bash shell/deploy-website.sh"
        return 0
    fi

    # 如果目录存在但不是 git，警告
    if [ -d "$WEBSITE_DIR" ] && [ -n "$(ls -A "$WEBSITE_DIR" 2>/dev/null)" ]; then
        log_error "$WEBSITE_DIR 已存在且非空，但不是 git 仓库。请手动清理后重试。"
        return 1
    fi

    # 父目录
    mkdir -p "$(dirname "$WEBSITE_DIR")"

    log_info "git clone..."
    if git clone --branch "$WEBSITE_BRANCH" --depth 20 "$WEBSITE_REPO" "$WEBSITE_DIR"; then
        log_ok "clone 完成"
    else
        log_error "git clone 失败"
        return 1
    fi

    # 仅在 nginx 容器在跑时才 reload
    if ensure_nginx_running; then
        nginx_test_config && nginx_reload
    else
        log_warn "nginx 还没启动，跳过 reload。等 nginx 起来后会自动加载 ${WEBSITE_DIR}"
    fi

    cmd_status
    print_visit_hint
}

# 日常更新：git pull
cmd_update() {
    log_info "============================================================"
    log_info "更新 fitcoach-website"
    log_info "============================================================"

    if [ ! -d "$WEBSITE_DIR/.git" ]; then
        log_error "$WEBSITE_DIR 还未初始化。请先执行：bash shell/deploy-website.sh init"
        return 1
    fi

    log_info "git fetch & pull（分支 $WEBSITE_BRANCH）..."
    if ! git -C "$WEBSITE_DIR" fetch --depth 20 origin "$WEBSITE_BRANCH"; then
        log_error "git fetch 失败"
        return 1
    fi

    local before after
    before=$(git -C "$WEBSITE_DIR" rev-parse HEAD)
    if ! git -C "$WEBSITE_DIR" reset --hard "origin/${WEBSITE_BRANCH}"; then
        log_error "git reset 失败"
        return 1
    fi
    after=$(git -C "$WEBSITE_DIR" rev-parse HEAD)

    if [ "$before" = "$after" ]; then
        log_warn "已是最新代码（${after:0:8}），无变更。仍然 reload 一次以保险。"
    else
        log_ok "代码已更新：${before:0:8} → ${after:0:8}"
    fi

    if ensure_nginx_running; then
        nginx_test_config && nginx_reload
    fi

    cmd_status
    print_visit_hint
}

# 部署状态
cmd_status() {
    echo ""
    log_info "============================================================"
    log_info "fitcoach-website 部署状态"
    log_info "============================================================"

    if [ ! -d "$WEBSITE_DIR" ]; then
        log_warn "目录不存在：$WEBSITE_DIR"
        return 1
    fi

    echo "目录：      $WEBSITE_DIR"
    echo "文件数：    $(find "$WEBSITE_DIR" -maxdepth 2 -type f 2>/dev/null | wc -l | tr -d ' ')"

    if [ -d "$WEBSITE_DIR/.git" ]; then
        echo "分支：      $(git -C "$WEBSITE_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null)"
        echo "最新提交：  $(git -C "$WEBSITE_DIR" log -1 --format='%h %s (%cr)' 2>/dev/null)"
    else
        log_warn "  ✗ 不是 git 仓库"
    fi

    echo ""
    if ensure_nginx_running 2>/dev/null; then
        log_info "本地 curl 验证（http://localhost/）"
        # 用 -I 拿 header；若状态码 200，再 grep <title> 看是不是占位文案
        curl -sI "http://localhost/" | head -3 || true
        echo ""
        local title
        title=$(curl -s "http://localhost/" | grep -oE '<title>[^<]*</title>' | head -1)
        if [ -n "$title" ]; then
            echo "  Title: $title"
        fi
    else
        log_warn "nginx 容器未运行，跳过 curl 验证"
    fi
}

print_visit_hint() {
    echo ""
    log_ok "============================================================"
    log_ok "完成！现在可访问："
    log_ok "  公网：   http://migofitai.com/"
    log_ok "  本机：   curl http://localhost/"
    log_ok "============================================================"
}

usage() {
    cat <<EOF
用法：
  bash shell/deploy-website.sh init      首次部署（git clone + nginx reload）
  bash shell/deploy-website.sh           日常更新（git pull + nginx reload）
  bash shell/deploy-website.sh update    同上（显式）
  bash shell/deploy-website.sh status    查看部署状态
  bash shell/deploy-website.sh help      显示本帮助

环境变量（可通过 .env.prod 覆盖）：
  WEBSITE_DIR      宿主机部署目录（默认 /data/fitcoach/website）
  WEBSITE_REPO     git 仓库地址（默认 github 上的 fitcoach-website）
  WEBSITE_BRANCH   git 分支（默认 main）
EOF
}

# 主入口
ACTION="${1:-update}"
case "$ACTION" in
    init)            cmd_init ;;
    update|"")       cmd_update ;;
    status)          cmd_status ;;
    -h|--help|help)  usage ;;
    *)
        log_error "未知子命令：$ACTION"
        echo ""
        usage
        exit 1
        ;;
esac
