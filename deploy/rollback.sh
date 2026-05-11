#!/usr/bin/env bash
# =========================================================
# 家庭账房 · 回滚到上一版 jar(运行于生产服务器)
#
# 用法:
#   sudo bash deploy/rollback.sh
#
# 干啥:
#   1. 把 /opt/finance/app.jar.prev 还原到 /opt/finance/app.jar
#   2. systemctl restart finance
#   3. /health 检查
#
# 不动 DB(因为多数迁移 backward-compat,老 jar 兼容新 schema)。
# 若 DB 也要回滚,看 /var/backup/finance/pre-deploy-*.sql.gz,手动 gunzip + mysql。
# =========================================================
set -euo pipefail

G=$'\033[32m'; R=$'\033[31m'; Y=$'\033[33m'; X=$'\033[0m'
ok()  { echo "${G}✓${X} $*"; }
err() { echo "${R}✗${X} $*" >&2; }
die() { err "$1"; exit 1; }

[[ $EUID -eq 0 ]] || die "必须 sudo 跑"
[[ -f /opt/finance/app.jar ]] || die "/opt/finance/app.jar 不存在,服务可能从未上线"
[[ -f /opt/finance/app.jar.prev ]] || die "/opt/finance/app.jar.prev 不存在,没有上一版可回滚"

# 检查 prev 和当前不一样,否则白回滚
if cmp -s /opt/finance/app.jar /opt/finance/app.jar.prev; then
  echo "${Y}⚠${X} app.jar 与 app.jar.prev 内容相同,回滚无意义"
  exit 0
fi

SERVER_PORT=$(grep '^SERVER_PORT=' /etc/finance.env 2>/dev/null | cut -d= -f2- | tr -d '"' || echo 20000)

echo "═══ 回滚 jar ═══"
# 当前 jar 暂存到 .reverted,以便万一回滚也挂了能再切回
cp /opt/finance/app.jar /opt/finance/app.jar.reverted-$(date +%s)
cp /opt/finance/app.jar.prev /opt/finance/app.jar
chown finance:finance /opt/finance/app.jar
ok "app.jar.prev → app.jar(原 jar 备份到 app.jar.reverted-* 以防万一)"

echo "═══ 重启 finance ═══"
systemctl restart finance

# /health 等 30 秒
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  sleep 2
  curl -sf "http://127.0.0.1:${SERVER_PORT}/health" >/dev/null 2>&1 && { ok "/health 200($((i*2))s)"; break; }
  [[ $i -eq 15 ]] && {
    journalctl -u finance --no-pager -n 30
    die "回滚后服务 30s 未起来,看 journalctl"
  }
done

echo
echo "${G}═══════════════════════════════════════${X}"
echo "${G}  回滚完成 · 现跑的是 app.jar.prev${X}"
echo "${G}═══════════════════════════════════════${X}"
echo
echo "若 DB 也需回滚(多数迁移 backward-compat 不用):"
echo "  ls /var/backup/finance/    # 找最近的 pre-deploy-*.sql.gz"
echo "  gunzip < /var/backup/finance/pre-deploy-XXX.sql.gz | mysql -ufinance -p\$PASS finance"
echo
echo "状态:sudo systemctl status finance"
echo "日志:sudo journalctl -u finance -f"
