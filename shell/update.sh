#!/usr/bin/env bash
# 已有生产库的一键更新：拉代码 → 检查迁移 → 构建 → 停写备份 → 迁移 → 启动验证。
# 服务器执行：bash shell/update.sh
set -Eeuo pipefail
umask 077
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

die() { echo "更新停止：$*" >&2; exit 1; }
if [[ "${1:-}" != --after-pull ]]; then
    [[ $# == 0 ]] || die '用法：bash shell/update.sh'
    command -v flock >/dev/null || die '需要 Linux flock（util-linux）'
    exec 9>"$(git rev-parse --git-path fitcoach-update.lock)"
    flock -n 9 || die '已有更新进程正在运行'
    git diff --quiet && git diff --cached --quiet || die '存在未提交的受跟踪文件改动，请先处理'
    echo '[1/7] 拉取最新代码'
    git pull --ff-only
    # 重新加载拉取后的脚本；文件描述符9保留部署锁。
    export FITCOACH_UPDATE_LOCK_HELD=1
    exec bash shell/update.sh --after-pull
fi
[[ "${FITCOACH_UPDATE_LOCK_HELD:-}" == 1 ]] && { : >&9; } 2>/dev/null || die '请从 bash shell/update.sh 启动'

for tool in docker sha256sum curl; do command -v "${tool}" >/dev/null || die "缺少命令：${tool}"; done
docker info >/dev/null
if docker compose version >/dev/null 2>&1; then COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null; then COMPOSE=(docker-compose)
else die '缺少 Docker Compose'; fi
[[ -f .env.prod ]] || die '缺少 .env.prod'
compose() { "${COMPOSE[@]}" --env-file .env.prod -f shell/docker-compose.prod.yml "$@"; }
db() {
    docker exec -i fitcoach-mysql-prod sh -ec '
      export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
      exec mysql -uroot --batch --skip-column-names "${MYSQL_DATABASE:-fitcoach}"
    '
}
query() { printf '%s\n' "$1" | db; }

echo '[2/7] 检查数据库与迁移清单'
[[ "$(query 'SELECT 1;')" == 1 ]] || die '数据库未就绪；本脚本用于已有数据库更新，不用于首次初始化'
ledger_exists=$(query "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='fitcoach_schema_migration';")
files=(); checks=(); hashes=(); known=(); pending_count=0
sql_dir=fitcoach-app/src/main/resources/sql
while IFS= read -r name || [[ -n "${name}" ]]; do
    [[ -z "${name}" || "${name}" == \#* ]] && continue
    [[ "${name}" =~ ^[0-9]{8}_[a-z0-9_]+\.sql$ ]] || die "非法迁移名：${name}"
    for previous in ${known[@]+"${known[@]}"}; do [[ "${previous}" != "${name}" ]] || die "重复迁移：${name}"; done
    known+=("${name}")
    file="${sql_dir}/${name}"; check="${sql_dir}/prechecks/${name}"
    [[ -s "${file}" && -s "${check}" ]] || die "迁移或检查文件缺失：${name}"
    hash=$(sha256sum "${file}" | cut -d ' ' -f1)
    history=''
    if [[ "${ledger_exists}" == 1 ]]; then
        history=$(query "SELECT CONCAT(checksum, ':', status) FROM fitcoach_schema_migration WHERE version='${name}';")
    fi
    if [[ -n "${history}" ]]; then
        [[ "${history}" == "${hash}:APPLIED" ]] || die "迁移曾失败/中断或已执行SQL被修改：${name}；核对数据库后人工修复，不自动重跑"
        echo "已执行，跳过：${name}"
        continue
    fi
    state=$(db < "${check}")
    [[ "${state}" == PENDING || "${state}" == APPLIED ]] || die "数据库结构不完整或前置条件不满足：${name}（${state}）"
    echo "迁移状态：${name} ${state}"
    files+=("${file}"); checks+=("${check}"); hashes+=("${hash}")
    pending_count=$((pending_count + 1))
done < "${sql_dir}/migrations.list"
if [[ "${ledger_exists}" == 1 ]]; then
    recorded=$(query 'SELECT version FROM fitcoach_schema_migration ORDER BY version;')
    while IFS= read -r version; do
        [[ -z "${version}" ]] && continue
        found=0
        for name in ${known[@]+"${known[@]}"}; do [[ "${name}" != "${version}" ]] || found=1; done
        [[ "${found}" == 1 ]] || die "数据库存在当前代码未登记的迁移：${version}；拒绝隐式降级"
    done <<< "${recorded}"
fi

# 先保留正在运行的旧镜像，再构建；失败时旧应用继续提供服务。
echo '[3/7] 保存旧镜像并构建新版本'
release_id="$(date +%Y%m%d-%H%M%S)-$$"
old_image=$(docker inspect --format '{{.Image}}' fitcoach-app-prod)
docker tag "${old_image}" "fitcoach-server:before-update-${release_id}"
compose build app

echo '[4/7] 停止应用写入并备份数据库'
backup_dir="${FITCOACH_BACKUP_DIR:-/data/fitcoach/backup}"
mkdir -p "${backup_dir}"
backup_file="${backup_dir}/fitcoach-${release_id}.sql"
stopped=0
on_failure() {
    local rc=$?
    trap - ERR
    echo "更新失败（退出码${rc}），备份位置：${backup_file}；旧镜像：fitcoach-server:before-update-${release_id}" >&2
    if [[ "${stopped}" == 1 ]]; then
        compose stop app || true
        echo '应用保持停止。DDL不保证回滚，请检查SQL记录和数据库；不自动恢复旧镜像或重跑失败迁移。' >&2
    fi
    exit "${rc}"
}
trap on_failure ERR
stopped=1
compose stop app
docker exec fitcoach-mysql-prod sh -ec '
  export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
  exec mysqldump -uroot --single-transaction --routines --events --triggers \
    --no-tablespaces --set-gtid-purged=OFF "${MYSQL_DATABASE:-fitcoach}"
' > "${backup_file}.partial"
test -s "${backup_file}.partial"
mv "${backup_file}.partial" "${backup_file}"
echo "备份成功：${backup_file}"

echo '[5/7] 执行尚未应用的迁移'
query "CREATE TABLE IF NOT EXISTS fitcoach_schema_migration (
 version VARCHAR(191) NOT NULL PRIMARY KEY,
 checksum CHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL,
 started_at DATETIME(6) NOT NULL,
 applied_at DATETIME(6) NULL,
 adopted BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
for ((i=0; i<pending_count; i++)); do
    name="${files[i]##*/}"
    # 停写后重新检查，避免预检与迁移间发生手工变更。
    state=$(db < "${checks[i]}")
    [[ "${state}" == PENDING || "${state}" == APPLIED ]] || { echo "迁移前结构变化：${name}" >&2; false; }
    query "INSERT INTO fitcoach_schema_migration(version, checksum, status, started_at, adopted)
      VALUES('${name}', '${hashes[i]}', 'STARTED', NOW(6), $([[ "${state}" == APPLIED ]] && echo TRUE || echo FALSE));"
    if [[ "${state}" == PENDING ]]; then
        echo "执行：${name}"
        db < "${files[i]}"
    else
        echo "核实已手工应用，登记基线：${name}"
    fi
    [[ "$(db < "${checks[i]}")" == APPLIED ]] || { echo "迁移后检查未通过：${name}" >&2; false; }
    query "UPDATE fitcoach_schema_migration SET status='APPLIED', applied_at=NOW(6) WHERE version='${name}';"
done

echo '[6/7] 启动服务（强制validate，不再拉取代码）'
JPA_DDL_AUTO=validate compose up -d app nginx
# app重建后地址可能变化，现有nginx容器需要重新解析上游地址。
compose exec -T nginx nginx -t
compose exec -T nginx nginx -s reload
echo '[7/7] 验证应用健康'
port_address=$(compose port nginx 443)
port="${port_address##*:}"
[[ "${port}" =~ ^[0-9]+$ ]] || { echo '无法获取Nginx HTTPS端口' >&2; false; }
health_host="${FITCOACH_HEALTH_HOST:-migofitai.com}"
[[ "${health_host}" =~ ^[a-zA-Z0-9.-]+$ ]] || { echo '健康检查域名不合法' >&2; false; }
healthy=0
for ((attempt=0; attempt<60; attempt++)); do
    # 本机连接但保留真实Host/SNI和证书校验，不受HTTP跳转或公网DNS影响。
    response_ok=0
    body=$(curl -fsS --noproxy '*' --max-time 3 \
        --resolve "${health_host}:${port}:127.0.0.1" \
        "https://${health_host}:${port}/api/auth/ping" 2>&1) && response_ok=1
    if [[ "${response_ok}" == 1 && "${body}" =~ \"code\"[[:space:]]*:[[:space:]]*0[[:space:]]*[,}] && "${body}" =~ \"data\"[[:space:]]*:[[:space:]]*\"pong\" ]]; then
        healthy=1; break
    fi
    sleep 2
done
[[ "${healthy}" == 1 ]] || { echo "HTTPS健康检查失败，最后响应：${body:0:300}" >&2; compose logs --tail=80 app; false; }
stopped=0
echo "更新成功：$(git rev-parse --short HEAD)；备份：${backup_file}"
