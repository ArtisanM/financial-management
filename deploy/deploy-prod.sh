#!/usr/bin/env bash
# =========================================================
# deploy/deploy-prod.sh · 家庭账房 · 单条命令完整生产部署
#
# 用法(本地终端):
#   bash deploy/deploy-prod.sh user@prod.host
#   REMOTE=user@prod.host bash deploy/deploy-prod.sh
#
# 比 deploy.sh 多做的事:
#   1. 预飞:本地 git tree 干净 + 远程可 ssh + 远程目录就位
#   2. 远程 mysqldump 到 /var/backup/finance/pre-deploy-{ts}.sql.gz(任何改动前)
#   3. 旧 jar 备份到 /opt/finance/app.jar.prev(失败可秒回)
#   4. db/apply.sh 走 schema_history 表幂等(已 apply 过的 V* 自动跳过)
#   5. 重启后双重健康检查(/health + 登录态 /dashboard)
#   6. 失败任意一步立即停 + 打印「回滚步骤」
#
# 假设远程已就位:
#   /etc/finance.env(含 DB_USER/DB_PASS/DB_NAME 等),deploy.sh 同款
#   /opt/finance/{app.jar, uploads/, logs/}
#   sudoers 条目允许 finance 用户:/bin/cp app.jar 到 /opt/finance/、
#                                /bin/systemctl {start,stop,restart,status} finance
#   port 20000(prod)或 8080(dev),用 SERVER_PORT 环境变量覆盖
# =========================================================
set -euo pipefail

# --- 配置 ---
REMOTE="${1:-${REMOTE:-}}"
[[ -n "$REMOTE" ]] || { echo "用法: bash deploy/deploy-prod.sh user@host" >&2; exit 1; }
REMOTE_DIR="${REMOTE_DIR:-/opt/finance}"
SERVER_PORT="${SERVER_PORT:-20000}"
BACKUP_DIR_REMOTE="${BACKUP_DIR_REMOTE:-/var/backup/finance}"
TS=$(date +%Y%m%d-%H%M%S)
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 颜色
G=$'\033[32m'; R=$'\033[31m'; Y=$'\033[33m'; B=$'\033[1;36m'; X=$'\033[0m'
say()  { echo "${B}=== $* ===${X}"; }
ok()   { echo "${G}✓${X} $*"; }
warn() { echo "${Y}⚠${X} $*"; }
die()  { echo "${R}✗${X} $*" >&2; print_rollback; exit 1; }

print_rollback() {
  echo
  echo "${R}════════════════ 回滚步骤(若需要)════════════════${X}"
  echo "1) 停服务 + 还原旧 jar:"
  echo "   ssh $REMOTE 'sudo /bin/systemctl stop finance && sudo /bin/cp $REMOTE_DIR/app.jar.prev $REMOTE_DIR/app.jar && sudo /bin/systemctl start finance'"
  echo
  echo "2) 还原 DB(若 V12 已应用):"
  echo "   ssh $REMOTE 'gunzip < $BACKUP_DIR_REMOTE/pre-deploy-${TS}.sql.gz | mysql -uROOT -pXXX finance'"
  echo "   再 DROP COLUMN logo_preset(若想完全回到 V11):"
  echo "   ssh $REMOTE 'mysql ... -e \"ALTER TABLE family DROP COLUMN logo_preset;\"'"
  echo "   或更简单:DELETE FROM schema_history WHERE filename='V12__family_logo_preset.sql';"
  echo "${R}═══════════════════════════════════════════════════${X}"
}

# --- 0. 预飞 ---
say "0/7 预飞检查"
cd "$ROOT_DIR"
git status --porcelain | grep -q . && die "本地 git tree 不干净,先 commit/stash"
ok "git tree clean(commit: $(git rev-parse --short HEAD))"
ssh -o ConnectTimeout=5 -o BatchMode=yes "$REMOTE" "echo ok" >/dev/null 2>&1 \
  || die "ssh $REMOTE 不通(检查 ssh-agent / known_hosts)"
ok "ssh $REMOTE 可达"
ssh "$REMOTE" "test -d $REMOTE_DIR && test -f /etc/finance.env" \
  || die "$REMOTE_DIR 或 /etc/finance.env 不存在,先按 deploy/finance.env.example 配置"
ok "$REMOTE_DIR + /etc/finance.env 就位"

# --- 1. 本地构建 ---
say "1/7 本地打包"
mvn -B -q -DskipTests package
JAR="$ROOT_DIR/target/app.jar"
[[ -f "$JAR" ]] || die "构建产物缺失:$JAR"
JAR_SIZE=$(stat -c%s "$JAR")
JAR_SHA=$(sha256sum "$JAR" | cut -d' ' -f1 | cut -c1-16)
ok "jar size=$(numfmt --to=iec $JAR_SIZE) sha256=${JAR_SHA}…"

# --- 2. 远程备份 DB ---
say "2/7 远程 mysqldump 到 $BACKUP_DIR_REMOTE/pre-deploy-${TS}.sql.gz"
ssh "$REMOTE" "
  set -euo pipefail
  set -a; . /etc/finance.env; set +a
  sudo mkdir -p $BACKUP_DIR_REMOTE
  sudo chown finance:finance $BACKUP_DIR_REMOTE
  mysqldump --single-transaction --quick \
    -h\"\${DB_HOST:-127.0.0.1}\" -P\"\${DB_PORT:-3306}\" \
    -u\"\$DB_USER\" -p\"\$DB_PASS\" \"\$DB_NAME\" \
    | gzip > $BACKUP_DIR_REMOTE/pre-deploy-${TS}.sql.gz
  size=\$(stat -c%s $BACKUP_DIR_REMOTE/pre-deploy-${TS}.sql.gz)
  [[ \$size -gt 1024 ]] || { echo '✗ mysqldump 产物 < 1KB,疑似失败' >&2; exit 1; }
  echo ok-\$size
" || die "DB 备份失败,中止部署"
ok "DB 备份完成"

# --- 3. 上传 jar(到 .new,不动旧 jar)+ 迁移 ---
say "3/7 上传 jar + 同步迁移文件"
ssh "$REMOTE" "mkdir -p $REMOTE_DIR/db/migration"
scp -q "$JAR" "$REMOTE:$REMOTE_DIR/app.jar.new" || die "scp jar 失败"
scp -q "$ROOT_DIR/db/apply.sh" "$REMOTE:$REMOTE_DIR/db/apply.sh"
scp -q "$ROOT_DIR/db/migration/"V*__*.sql "$REMOTE:$REMOTE_DIR/db/migration/" || die "scp migrations 失败"
ok "上传完成"

# --- 4. 应用迁移(幂等)---
say "4/7 应用 V*__*.sql(schema_history 自动跳已执行)"
ssh "$REMOTE" "
  set -euo pipefail
  set -a; . /etc/finance.env; set +a
  cd $REMOTE_DIR
  chmod +x db/apply.sh
  bash db/apply.sh
" || die "迁移失败,服务未重启,旧 jar 仍在跑"
ok "迁移完成"

# --- 5. 切换 jar + 重启 ---
say "5/7 备份旧 jar + 切新 jar + 重启 systemd"
ssh "$REMOTE" "
  set -euo pipefail
  if [[ -f $REMOTE_DIR/app.jar ]]; then
    sudo /bin/cp $REMOTE_DIR/app.jar $REMOTE_DIR/app.jar.prev
  fi
  sudo /bin/cp $REMOTE_DIR/app.jar.new $REMOTE_DIR/app.jar
  rm -f $REMOTE_DIR/app.jar.new
  sudo /bin/systemctl restart finance
" || die "切换 jar / 重启失败"
ok "jar 切换 + restart 触发"

# --- 6. 等待启动 ---
say "6/7 等待应用启动"
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  sleep 2
  if ssh "$REMOTE" "curl -sf -o /dev/null -w '%{http_code}' http://127.0.0.1:$SERVER_PORT/health" 2>/dev/null | grep -q 200; then
    ok "/health 200(${i}× 2s = $((i*2))s)"
    break
  fi
  [[ $i -eq 12 ]] && die "应用 24s 未起来,看日志:ssh $REMOTE 'tail -50 $REMOTE_DIR/logs/app.log'"
done

# --- 7. 验收烟测 ---
say "7/7 验收烟测"
HEALTH=$(ssh "$REMOTE" "curl -sf http://127.0.0.1:$SERVER_PORT/health")
[[ "$HEALTH" == *'"status":"UP"'* ]] && ok "/health = $HEALTH" || die "/health 内容异常: $HEALTH"

# 登录后访问 /dashboard,验证完整渲染(到 </html>)
ssh "$REMOTE" "
  set -euo pipefail
  C=/tmp/deploy-smoke-$$.txt
  rm -f \$C
  TOKEN=\$(curl -s -c \$C http://127.0.0.1:$SERVER_PORT/login | grep -oE 'name=\"_csrf\" value=\"[^\"]+\"' | head -1 | sed 's/.*value=\"//;s/\"//')
  curl -s -b \$C -c \$C -X POST http://127.0.0.1:$SERVER_PORT/login \
    --data-urlencode 'username=diwa' \
    --data-urlencode 'password=demo1234' \
    --data-urlencode \"_csrf=\$TOKEN\" -o /dev/null -w '%{http_code}\n' \
    | grep -q 302 || { echo '✗ 登录失败' >&2; exit 1; }
  curl -sf -b \$C http://127.0.0.1:$SERVER_PORT/dashboard | grep -q '</html>' \
    || { echo '✗ /dashboard 渲染不完整' >&2; exit 1; }
  rm -f \$C
" && ok "/dashboard 登录后渲染完整" || die "登录或 /dashboard 烟测失败"

echo
echo "${G}═══════════════════════════════════════════${X}"
echo "${G} 部署成功 · $REMOTE · commit $(git rev-parse --short HEAD)${X}"
echo "${G}═══════════════════════════════════════════${X}"
echo "DB 备份:    $BACKUP_DIR_REMOTE/pre-deploy-${TS}.sql.gz"
echo "旧 jar:     $REMOTE_DIR/app.jar.prev"
echo
echo "若 24h 内一切正常,可清旧 jar:"
echo "  ssh $REMOTE 'rm $REMOTE_DIR/app.jar.prev'"
echo
echo "若需回滚,见上方失败时打印的回滚步骤(本次成功未打印,这里再贴一份):"
print_rollback
