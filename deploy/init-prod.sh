#!/usr/bin/env bash
# =========================================================
# deploy/init-prod.sh · 对方机器零基础首次部署
#
# 用法(在对方机器上,推送过 staging 后):
#   cd ~/finance-deploy
#   sudo bash deploy/init-prod.sh
#
# 这个脚本是幂等的:重跑只补做缺的步骤,已做过的 skip。
# 中途任何一步失败 → 立即停 + 打印当前状态,不留半完成态。
#
# 目标:从空 Ubuntu 22+/Debian 12+ 装到能访问 /dashboard,全自动
# (DB 密码 / 管理员密码等需要交互输入,默认值给得保守)。
#
# 如果对方机器是 RHEL/CentOS/Alibaba Cloud Linux,需要把 apt 那段
# 改成 dnf/yum + openjdk 包名调整。其余部分(systemd / 文件路径)
# 都是通用的。
# =========================================================
set -euo pipefail

# ---------- 颜色 ----------
G=$'\033[32m'; R=$'\033[31m'; Y=$'\033[33m'; B=$'\033[1;36m'; X=$'\033[0m'
say()  { echo; echo "${B}═══ $* ═══${X}"; }
ok()   { echo "${G}✓${X} $*"; }
warn() { echo "${Y}⚠${X} $*"; }
die()  { echo "${R}✗${X} $*" >&2; exit 1; }
ask()  { local q="$1" def="${2:-}"; local ans; read -p "$q${def:+ [$def]}: " ans; echo "${ans:-$def}"; }
ask_pw() { local q="$1"; local ans; read -s -p "$q: " ans; echo >&2; echo "$ans"; }

# ---------- 0. 预飞 ----------
say "0/12 预飞检查"
[[ $EUID -eq 0 ]] || die "必须用 sudo 跑"
[[ -f app.jar ]]   || die "app.jar 不在当前目录,先 cd ~/finance-deploy"
[[ -d db/migration ]] || die "db/migration 缺失"
[[ -f deploy/finance.service ]] || die "deploy/finance.service 缺失"
ok "在 staging 目录:$(pwd)"

# 检测 OS
if   [[ -f /etc/debian_version ]]; then OS=debian
elif [[ -f /etc/redhat-release ]]; then OS=rhel
else die "未识别的 OS;init-prod.sh 仅在 Debian/Ubuntu 测试过"; fi
ok "OS = $OS"

# ---------- 1. 装 Java 21 ----------
say "1/12 安装 Java 21"
if java -version 2>&1 | head -1 | grep -qE 'version "(21|22|23|24)\.'; then
  ok "java 已存在:$(java -version 2>&1 | head -1)"
else
  if [[ "$OS" == "debian" ]]; then
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-21-jdk-headless
  else
    dnf install -y java-21-openjdk-headless
  fi
  ok "java 已装:$(java -version 2>&1 | head -1)"
fi

# 校准 systemd 里 java 的绝对路径(各发行版可能不同)
JAVA_BIN=$(readlink -f "$(command -v java)")
[[ -x "$JAVA_BIN" ]] || die "找不到可执行 java 二进制"
ok "java bin = $JAVA_BIN"

# ---------- 2. 装 MySQL 8 ----------
say "2/12 安装 MySQL 8"
if systemctl is-active --quiet mysql 2>/dev/null || systemctl is-active --quiet mysqld 2>/dev/null; then
  ok "mysql 已在跑"
else
  if [[ "$OS" == "debian" ]]; then
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq mysql-server
  else
    dnf install -y mysql-server
  fi
  systemctl enable --now mysql 2>/dev/null || systemctl enable --now mysqld
  ok "mysql 已启动"
fi

# ---------- 3. 创建 finance 系统用户 ----------
say "3/12 创建 finance 系统用户"
if id finance >/dev/null 2>&1; then
  ok "finance 用户已存在(uid=$(id -u finance))"
else
  useradd -r -m -d /home/finance -s /bin/bash finance
  ok "finance 用户已创建"
fi

# ---------- 4. 创建目录结构 ----------
say "4/12 创建目录结构"
install -d -o finance -g finance -m 755 /opt/finance/{logs,db,db/migration}
install -d -o finance -g finance -m 755 /var/finance/uploads
install -d -o finance -g finance -m 755 /var/backup/finance
ok "/opt/finance + /var/finance/uploads + /var/backup/finance 就位"

# ---------- 5. 配置 MySQL 库 + 用户 ----------
say "5/12 配置 MySQL 库 + finance 用户"
DB_NAME="${DB_NAME:-finance}"
DB_USER="${DB_USER:-finance}"

if mysql -sN -e "SHOW DATABASES" 2>/dev/null | grep -qx "$DB_NAME"; then
  ok "DB $DB_NAME 已存在"
  # 假设之前已经初始化过,DB_PASS 应能从 /etc/finance.env 读到
  if [[ -f /etc/finance.env ]]; then
    DB_PASS=$(grep '^DB_PASS=' /etc/finance.env | cut -d= -f2-)
    ok "DB_PASS 从 /etc/finance.env 读到"
  else
    DB_PASS=$(ask_pw "现有 DB ${DB_USER} 用户的密码(在 /etc/finance.env 之外)")
  fi
else
  warn "DB $DB_NAME 不存在,即将创建"
  DB_PASS_GEN=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)
  DB_PASS=$(ask_pw "为 ${DB_USER}@localhost 设密码(回车 = 自动生成,生成的将打印一次)")
  [[ -z "$DB_PASS" ]] && { DB_PASS="$DB_PASS_GEN"; warn "已生成密码:$DB_PASS  (后面写到 /etc/finance.env,请记下)"; }
  mysql <<SQL
CREATE DATABASE \`${DB_NAME}\` DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL
  ok "DB + 用户已创建,GRANT ALL on ${DB_NAME}.* to ${DB_USER}@localhost"
fi

# 自检:用 finance 用户登录
mysql -h127.0.0.1 -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SELECT 1" >/dev/null 2>&1 \
  || die "用 ${DB_USER} 登录失败,密码可能错"
ok "${DB_USER}@localhost 登录验证通过"

# ---------- 6. 写 /etc/finance.env ----------
say "6/12 写 /etc/finance.env"
if [[ -f /etc/finance.env ]]; then
  ok "/etc/finance.env 已存在,跳过(若需重置 → 先 rm 再重跑此脚本)"
else
  REMEMBER_KEY=$(openssl rand -hex 32)
  SERVER_PORT=$(ask "服务监听端口" "20000")
  cat > /etc/finance.env <<EOF
# /etc/finance.env — 由 deploy/init-prod.sh 生成 $(date -Iseconds)
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=${DB_NAME}
DB_USER=${DB_USER}
DB_PASS=${DB_PASS}

UPLOAD_ROOT=/var/finance/uploads
REMEMBER_ME_KEY=${REMEMBER_KEY}
BACKUP_DIR=/var/backup/finance
RETENTION_DAYS=56
SERVER_PORT=${SERVER_PORT}
FAMILY_ID=1

# 可选:LLM 集成(v0.2 资产体检 AI 综合诊断,留空则降级到本地规则)
# FINANCE_LLM_QWEN_API_KEY=
# FINANCE_LLM_DEEPSEEK_API_KEY=
EOF
  chmod 600 /etc/finance.env
  chown root:finance /etc/finance.env
  chmod 640 /etc/finance.env
  ok "/etc/finance.env 已写入(权限 640,组 finance 可读)"
fi

# ---------- 7. 跑数据库迁移(幂等)----------
say "7/12 应用数据库迁移(V1..V12)"
cp -f db/apply.sh /opt/finance/db/apply.sh
cp -f db/migration/V*__*.sql /opt/finance/db/migration/
chmod +x /opt/finance/db/apply.sh
chown -R finance:finance /opt/finance/db

set -a; . /etc/finance.env; set +a
sudo -u finance -E bash /opt/finance/db/apply.sh
ok "迁移完毕(已 apply 过的 V* 自动跳过)"

# ---------- 8. 提示 dev seed 风险 ----------
say "8/12 dev seed 数据风险提示"
ACCT_COUNT=$(mysql -h127.0.0.1 -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -sN -e "SELECT COUNT(*) FROM account" 2>/dev/null || echo 0)
if [[ "${ACCT_COUNT:-0}" -gt 5 ]]; then
  warn "DB 含 ${ACCT_COUNT} 个账户,V3/V4/V5 灌入了 11 个 demo 账户 + 12 个历史周期(2025-05~2026-04)"
  warn "首次 prod 上线建议:登入后到 /admin/accounts 把 demo 账户归档,或手工 TRUNCATE:"
  echo "    mysql -u${DB_USER} -p ${DB_NAME} <<EOF"
  echo "    DELETE FROM cash_flow; DELETE FROM transfer; DELETE FROM period_snapshot;"
  echo "    DELETE FROM snapshot_todo; DELETE FROM period_member_completion;"
  echo "    DELETE FROM fx_rate; DELETE FROM metrics_recompute_log;"
  echo "    DELETE FROM period; DELETE FROM account WHERE id > 0;"
  echo "    EOF"
  echo "    然后访问 /admin/periods 点 '立即开下一周期' 起一个干净的 OPEN 周期"
fi

# ---------- 9. 把 jar 放到 /opt/finance/ ----------
say "9/12 部署 app.jar"
install -m 644 -o finance -g finance app.jar /opt/finance/app.jar
ok "/opt/finance/app.jar 就位($(stat -c%s /opt/finance/app.jar) bytes)"

# ---------- 10. 装 systemd unit + sudoers ----------
say "10/12 装 systemd unit + sudoers"
# 把 deploy/finance.service 里的 java 路径校准到当前发行版(JAVA_BIN)
sed -e "s|/usr/lib/jvm/java-21-openjdk-amd64/bin/java|${JAVA_BIN}|" \
    deploy/finance.service > /etc/systemd/system/finance.service
chmod 644 /etc/systemd/system/finance.service
systemctl daemon-reload
systemctl enable finance
ok "systemd unit 安装 + enable"

# Sudoers — 让 finance 可以无密码 cp + restart(供 deploy-prod.sh 用)
cat > /etc/sudoers.d/finance <<EOF
# 允许 finance 用户做这几件事(供后续 deploy-prod.sh 使用)
finance ALL=(root) NOPASSWD: /bin/cp /home/finance/finance-deploy/app.jar /opt/finance/app.jar
finance ALL=(root) NOPASSWD: /bin/cp /opt/finance/app.jar.new /opt/finance/app.jar
finance ALL=(root) NOPASSWD: /bin/cp /opt/finance/app.jar /opt/finance/app.jar.prev
finance ALL=(root) NOPASSWD: /bin/cp /opt/finance/app.jar.prev /opt/finance/app.jar
finance ALL=(root) NOPASSWD: /bin/systemctl start finance
finance ALL=(root) NOPASSWD: /bin/systemctl stop finance
finance ALL=(root) NOPASSWD: /bin/systemctl restart finance
finance ALL=(root) NOPASSWD: /bin/systemctl status finance
finance ALL=(root) NOPASSWD: /bin/journalctl -u finance *
EOF
chmod 440 /etc/sudoers.d/finance
visudo -cf /etc/sudoers.d/finance >/dev/null && ok "/etc/sudoers.d/finance 写入 + 语法校验通过" \
  || die "sudoers 语法错误,已 abort(检查 /etc/sudoers.d/finance)"

# ---------- 11. 装备份 systemd timer(可选)----------
say "11/12 安装备份 timer(deploy/finance-backup.{service,timer})"
if [[ -f deploy/backup.sh && -f deploy/finance-backup.service && -f deploy/finance-backup.timer ]]; then
  install -m 755 -o finance -g finance deploy/backup.sh /opt/finance/backup.sh
  install -m 644 deploy/finance-backup.service /etc/systemd/system/finance-backup.service
  install -m 644 deploy/finance-backup.timer   /etc/systemd/system/finance-backup.timer
  systemctl daemon-reload
  systemctl enable --now finance-backup.timer
  ok "备份 timer 已 enable + start"
else
  warn "未找到 backup 模板,跳过(后续可手动装)"
fi

# ---------- 12. 启服务 + 健康检查 ----------
say "12/12 启动 finance 服务 + 健康检查"
systemctl restart finance
SERVER_PORT=$(grep '^SERVER_PORT=' /etc/finance.env | cut -d= -f2- | tr -d '"' || echo 20000)
SERVER_PORT="${SERVER_PORT:-20000}"

for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  sleep 2
  code=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${SERVER_PORT}/health" || echo 000)
  if [[ "$code" == "200" ]]; then
    ok "/health 200(${i}× 2s = $((i*2))s)"
    break
  fi
  [[ $i -eq 15 ]] && {
    journalctl -u finance --no-pager -n 30
    die "应用 30s 未起来,看上方 journalctl 日志"
  }
done

HEALTH=$(curl -s "http://127.0.0.1:${SERVER_PORT}/health")
[[ "$HEALTH" == *'"status":"UP"'* ]] && ok "/health = $HEALTH" || die "/health 内容异常: $HEALTH"

# ---------- 13. 可选:nginx :80 反代 ----------
say "13/13 nginx 反代 :80 → :${SERVER_PORT}(可选,但 prod 强烈建议)"
read -p "现在装 nginx 反代到 :80 吗? [Y/n] " yn
if [[ "$yn" != "n" && "$yn" != "N" ]]; then
  SN=$(ask "nginx server_name(域名,纯 IP 访问就回车用 _)" "_")
  bash deploy/nginx-setup.sh "$SN"
  NGINX_DONE=1
else
  NGINX_DONE=0
  warn "已跳过 nginx;若需要后续再跑:sudo bash deploy/nginx-setup.sh"
fi

echo
echo "${G}══════════════════════════════════════════════${X}"
echo "${G}  首次部署完成 · 应用已在 :${SERVER_PORT} 监听  ${X}"
echo "${G}══════════════════════════════════════════════${X}"
echo
echo "下一步:"
if [[ "${NGINX_DONE:-0}" == "1" ]]; then
  echo "  1) 浏览器访问  http://<server-ip>/login(nginx :80 反代)"
else
  echo "  1) 浏览器访问  http://<server-ip>:${SERVER_PORT}/login"
fi
echo "     默认账号:diwa / demo1234(由 V2__seed.sql 灌入)"
echo "     首次登录后立刻去 /profile/password 改密码!!"
echo
echo "  2) 若 ${ACCT_COUNT:-0} 个账户里包含 dev demo,建议先到"
echo "     /admin/accounts 归档不需要的账户,或按上面 §8 的 SQL 清空"
echo
echo "  3) 把域名反代到 :${SERVER_PORT}(可选,见 deploy/nginx-finance.conf.example)"
echo
echo "  4) 后续迭代发版只需在本地跑:"
echo "     bash deploy/push-to-prod.sh user@\$THIS_HOST"
echo "     ssh user@\$THIS_HOST 'cd ~/finance-deploy && sudo bash deploy/deploy-prod.sh \$REMOTE'"
echo "     (deploy-prod.sh 自动备份 DB / 旧 jar / 健康检查 / 失败回滚提示)"
echo
echo "调试:"
echo "  日志:  sudo journalctl -u finance -f"
echo "  状态:  sudo systemctl status finance"
echo "  备份:  ls /var/backup/finance/"
