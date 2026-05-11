#!/usr/bin/env bash
# =========================================================
# deploy/deploy-prod.sh · 家庭账房 · 单条命令完整生产部署
#
# 用法(本地终端):
#   bash deploy/deploy-prod.sh user@prod.host
#   REMOTE=user@prod.host bash deploy/deploy-prod.sh
#
# v0.2.1 强化(2026-05-11):
#   1. 预飞:git tree 干净 + ssh 可达 + /etc/finance.env 就位
#   2. 本地 mvn package
#   3. 远程 mysqldump 到 /var/backup/finance/pre-deploy-{ts}.sql.gz
#   4. ★ 迁移预览:列出本次会 apply 的 V*.sql,read 确认才继续
#   5. 上传 jar(到 .new) + 同步迁移文件
#   6. 应用迁移(schema_history 幂等)
#   7. 切 jar(旧的备份到 .prev) + systemctl restart
#   8. ★ 健康检查 / 烟测失败时自动回滚 jar 到 .prev(DB 备份不动)
#   9. 烟测改为不依赖具体凭据(GET /login 见 _csrf input + GET /health=UP)
#
# 假设远程已就位(由 init-prod.sh 配过):
#   /etc/finance.env(含 DB_USER/DB_PASS/DB_NAME 等)
#   /opt/finance/{app.jar, uploads/, logs/}
#   /etc/sudoers.d/finance 含 NOPASSWD /bin/cp + /bin/systemctl 白名单
# =========================================================
set -euo pipefail

# --- 配置 ---
REMOTE="${1:-${REMOTE:-}}"
[[ -n "$REMOTE" ]] || { echo "用法: bash deploy/deploy-prod.sh user@host" >&2; exit 1; }
REMOTE_DIR="${REMOTE_DIR:-/opt/finance}"
SERVER_PORT="${SERVER_PORT:-20000}"
BACKUP_DIR_REMOTE="${BACKUP_DIR_REMOTE:-/var/backup/finance}"
SKIP_MIGRATION_CONFIRM="${SKIP_MIGRATION_CONFIRM:-0}"   # CI 模式可设 1 跳过 read 确认
TS=$(date +%Y%m%d-%H%M%S)
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 颜色
G=$'\033[32m'; R=$'\033[31m'; Y=$'\033[33m'; B=$'\033[1;36m'; X=$'\033[0m'
say()  { echo; echo "${B}═══ $* ═══${X}"; }
ok()   { echo "${G}✓${X} $*"; }
warn() { echo "${Y}⚠${X} $*"; }
err()  { echo "${R}✗${X} $*" >&2; }

# 自动回滚 jar(DB 备份保留,人工评估是否还原)
auto_rollback_jar() {
  echo
  echo "${R}══════ 自动回滚 jar (DB 不动) ══════${X}"
  ssh "$REMOTE" "
    set +e
    sudo /bin/systemctl stop finance
    if [[ -f $REMOTE_DIR/app.jar.prev ]]; then
      sudo /bin/cp $REMOTE_DIR/app.jar.prev $REMOTE_DIR/app.jar
      echo '[rollback] app.jar.prev 已还原到 app.jar'
    else
      echo '[rollback] WARN: 没有 app.jar.prev,无法 jar 自动回滚'
    fi
    sudo /bin/systemctl start finance
  " || true
  echo
  echo "${Y}注意:DB 备份未自动还原(因为 schema migration 通常 backward-compat 设计,"
  echo "      老 jar 兼容新 schema 应能正常跑)。${X}"
  echo "若 DB 也需还原,手动:"
  echo "  ssh $REMOTE 'gunzip < $BACKUP_DIR_REMOTE/pre-deploy-${TS}.sql.gz | mysql -uROOT -pXXX finance'"
  echo "  (DROP COLUMN 撤新 schema 之前先确认 SQL 内容)"
}

die_with_rollback() {
  err "$1"
  auto_rollback_jar
  exit 1
}

die_no_rollback() {
  # 用于切 jar 之前的失败(此时还没动 jar / 服务,无需回滚)
  err "$1"
  exit 1
}

# --- 0. 预飞 ---
say "0/7 预飞检查"
cd "$ROOT_DIR"
git status --porcelain | grep -q . && die_no_rollback "本地 git tree 不干净,先 commit/stash"
ok "git tree clean(commit: $(git rev-parse --short HEAD))"
ssh -o ConnectTimeout=5 -o BatchMode=yes "$REMOTE" "echo ok" >/dev/null 2>&1 \
  || die_no_rollback "ssh $REMOTE 不通(检查 ssh-agent / known_hosts)"
ok "ssh $REMOTE 可达"
ssh "$REMOTE" "test -d $REMOTE_DIR && test -f /etc/finance.env" \
  || die_no_rollback "$REMOTE_DIR 或 /etc/finance.env 不存在,先按 init-prod.sh 完成首次部署"
ok "$REMOTE_DIR + /etc/finance.env 就位"

# --- 1. 本地构建 ---
say "1/7 本地打包(~10-15s)"
set +e
mvn -B -DskipTests package 2>&1 \
  | grep -vE '^\[INFO\] (Downloading|Downloaded|Progress |  )'
MVN_RC=${PIPESTATUS[0]}
set -e
[[ $MVN_RC -eq 0 ]] || die_no_rollback "mvn package 失败"
JAR="$ROOT_DIR/target/app.jar"
[[ -f "$JAR" ]] || die_no_rollback "构建产物缺失:$JAR"
JAR_SIZE=$(stat -c%s "$JAR")
JAR_SHA=$(sha256sum "$JAR" | cut -d' ' -f1 | cut -c1-16)
ok "jar size=$(numfmt --to=iec $JAR_SIZE) sha256=${JAR_SHA}…"

# --- 2. 远程备份 DB ---
say "2/7 远程 mysqldump → $BACKUP_DIR_REMOTE/pre-deploy-${TS}.sql.gz"
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
  gunzip -t $BACKUP_DIR_REMOTE/pre-deploy-${TS}.sql.gz || { echo '✗ gzip 完整性校验失败' >&2; exit 1; }
  echo \"ok size=\$(numfmt --to=iec \$size)\"
" || die_no_rollback "DB 备份失败,中止部署(此时未动任何东西)"
ok "DB 备份完成 + gunzip -t 校验通过"

# --- 3. 上传 jar(到 .new,不动旧 jar)+ 迁移 ---
say "3/7 上传 jar + 同步迁移文件"
ssh "$REMOTE" "mkdir -p $REMOTE_DIR/db/migration"
scp -q "$JAR" "$REMOTE:$REMOTE_DIR/app.jar.new" || die_no_rollback "scp jar 失败"
scp -q "$ROOT_DIR/db/apply.sh" "$REMOTE:$REMOTE_DIR/db/apply.sh"
scp -q "$ROOT_DIR/db/migration/"V*__*.sql "$REMOTE:$REMOTE_DIR/db/migration/" || die_no_rollback "scp migrations 失败"
ok "上传完成"

# --- 4. 迁移预览 + 用户确认 ---
say "4/7 列出本次将 apply 的 V*.sql(已 apply 过的自动跳过)"
PENDING=$(ssh "$REMOTE" "
  set -a; . /etc/finance.env; set +a
  cd $REMOTE_DIR
  for f in db/migration/V*__*.sql; do
    name=\$(basename \"\$f\")
    in_history=\$(mysql -h127.0.0.1 -u\"\$DB_USER\" -p\"\$DB_PASS\" \"\$DB_NAME\" -sN \
      -e \"SELECT 1 FROM schema_history WHERE filename='\$name'\" 2>/dev/null)
    if [[ -z \"\$in_history\" ]]; then
      echo \"\$name\"
    fi
  done
" 2>/dev/null || true)

if [[ -z "$PENDING" ]]; then
  ok "无新迁移(全部已 apply)"
else
  echo "${Y}本次将 apply 的迁移:${X}"
  while IFS= read -r f; do
    echo "  ${Y}→${X} $f"
    [[ -f "$ROOT_DIR/db/migration/$f" ]] && head -3 "$ROOT_DIR/db/migration/$f" | sed 's/^/      /'
  done <<< "$PENDING"
  echo
  if [[ "$SKIP_MIGRATION_CONFIRM" != "1" ]]; then
    if [[ ! -t 0 ]]; then
      warn "非交互式 stdin · 跳过 read 确认(若要拦截,在交互终端跑或 SKIP_MIGRATION_CONFIRM=0)"
    else
      read -p "${B}确认对 prod 应用这些迁移? [y/N]${X} " yn
      [[ "$yn" == "y" || "$yn" == "Y" ]] || die_no_rollback "用户取消;未动 prod DB"
    fi
  fi
fi

# --- 5. 应用迁移(此时 jar 还没切,失败仍可不回滚 jar)---
say "5/7 应用 V*__*.sql(schema_history 幂等)"
ssh "$REMOTE" "
  set -euo pipefail
  set -a; . /etc/finance.env; set +a
  cd $REMOTE_DIR
  chmod +x db/apply.sh
  bash db/apply.sh
" || die_no_rollback "迁移失败 · 旧 jar 仍在跑,DB 备份在 $BACKUP_DIR_REMOTE/pre-deploy-${TS}.sql.gz"
ok "迁移完成"

# --- 6. 切 jar + restart(从这步开始,失败要 auto rollback jar)---
say "6/7 备份旧 jar 到 .prev + 切新 jar + restart finance"
ssh "$REMOTE" "
  set -euo pipefail
  if [[ -f $REMOTE_DIR/app.jar ]]; then
    sudo /bin/cp $REMOTE_DIR/app.jar $REMOTE_DIR/app.jar.prev
  fi
  sudo /bin/cp $REMOTE_DIR/app.jar.new $REMOTE_DIR/app.jar
  rm -f $REMOTE_DIR/app.jar.new
  sudo /bin/systemctl restart finance
" || die_with_rollback "切换 jar / 重启失败"
ok "jar 切换 + restart 触发"

# --- 7. 健康检查 + 烟测(失败 → auto rollback jar)---
say "7/7 等待启动 + 烟测"
HEALTH_OK=0
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  sleep 2
  if ssh "$REMOTE" "curl -sf -o /dev/null -w '%{http_code}' http://127.0.0.1:$SERVER_PORT/health" 2>/dev/null | grep -q 200; then
    ok "/health 200(${i}× 2s = $((i*2))s)"
    HEALTH_OK=1
    break
  fi
done
[[ $HEALTH_OK -eq 1 ]] || {
  echo
  ssh "$REMOTE" "sudo /bin/journalctl -u finance --no-pager -n 30" 2>/dev/null | tail -20
  die_with_rollback "应用 24s 未起来,journalctl 见上"
}

HEALTH=$(ssh "$REMOTE" "curl -sf http://127.0.0.1:$SERVER_PORT/health")
[[ "$HEALTH" == *'"status":"UP"'* ]] || die_with_rollback "/health 内容异常: $HEALTH"
ok "/health = $HEALTH"

# 烟测改为不依赖具体凭据:GET /login 返回 200 + 含 _csrf input + GET /dashboard 重定向到 /login
ssh "$REMOTE" "
  set -euo pipefail
  body=\$(curl -sf http://127.0.0.1:$SERVER_PORT/login)
  echo \"\$body\" | grep -q 'name=\"_csrf\"' || { echo '✗ /login 不含 _csrf input' >&2; exit 1; }
  echo \"\$body\" | grep -q '</html>' || { echo '✗ /login 渲染不完整' >&2; exit 1; }
  code=\$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$SERVER_PORT/dashboard)
  [[ \"\$code\" == 302 || \"\$code\" == 200 ]] || { echo \"✗ /dashboard code=\$code\" >&2; exit 1; }
" || die_with_rollback "烟测失败(/login 或 /dashboard)"
ok "/login + /dashboard 烟测通过"

echo
echo "${G}═══════════════════════════════════════════${X}"
echo "${G}  部署成功 · $REMOTE · commit $(git rev-parse --short HEAD)${X}"
echo "${G}═══════════════════════════════════════════${X}"
echo "DB 备份:  $BACKUP_DIR_REMOTE/pre-deploy-${TS}.sql.gz"
echo "旧 jar:   $REMOTE_DIR/app.jar.prev"
echo
echo "24h 内一切正常,可清旧 jar:"
echo "  ssh $REMOTE 'rm $REMOTE_DIR/app.jar.prev'"
echo
echo "若 24h 内发现回归,人工回滚:"
echo "  ssh $REMOTE 'sudo /bin/systemctl stop finance && sudo /bin/cp $REMOTE_DIR/app.jar.prev $REMOTE_DIR/app.jar && sudo /bin/systemctl start finance'"
