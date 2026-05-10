#!/usr/bin/env bash
# =========================================================
# deploy/nginx-setup.sh · 装 nginx 反代 :80 → :20000(或自定义端口)
#
# 用法(在 prod 机器,sudo):
#   sudo bash deploy/nginx-setup.sh                       # 全自动,用 /etc/finance.env 的 SERVER_PORT
#   sudo bash deploy/nginx-setup.sh finance.example.com   # 指定 server_name
#   sudo NGINX_PORT=8080 bash deploy/nginx-setup.sh       # 自定义后端端口
#
# 这个脚本幂等:重跑只补做缺的步骤。
#
# 它做 5 件事:
#   1. 装 nginx(若没装)
#   2. 把 deploy/nginx-finance.conf.example 渲染到 /etc/nginx/sites-available/finance.conf
#      (替换 __PORT__ + __SERVER_NAME__ 占位)
#   3. 在 /etc/nginx/sites-enabled/ 起 finance.conf 的软链;移除默认 80 站点(避免冲突)
#   4. 把 Spring 绑到 127.0.0.1(写 /etc/finance.env 加 SERVER_ADDRESS=127.0.0.1),
#      重启 finance 服务 — 这样 :20000 只对 nginx 开放,外网只能走 :80
#   5. nginx -t 校验 + reload
# =========================================================
set -euo pipefail

G=$'\033[32m'; R=$'\033[31m'; Y=$'\033[33m'; B=$'\033[1;36m'; X=$'\033[0m'
say()  { echo; echo "${B}═══ $* ═══${X}"; }
ok()   { echo "${G}✓${X} $*"; }
warn() { echo "${Y}⚠${X} $*"; }
die()  { echo "${R}✗${X} $*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "必须用 sudo 跑"
[[ -f deploy/nginx-finance.conf.example ]] || die "找不到 deploy/nginx-finance.conf.example,先 cd 到 ~/finance-deploy"

SERVER_NAME="${1:-_}"            # 默认 "_" 表示匹配任意 Host(纯 IP 访问也 OK)
[[ -f /etc/finance.env ]] || die "/etc/finance.env 缺失,先跑 init-prod.sh"
PORT="${NGINX_PORT:-$(grep '^SERVER_PORT=' /etc/finance.env | cut -d= -f2- | tr -d '"' || echo 20000)}"
PORT="${PORT:-20000}"
ok "后端端口 = ${PORT},server_name = ${SERVER_NAME}"

# ---------- 1. 装 nginx ----------
say "1/5 安装 nginx"
if command -v nginx >/dev/null 2>&1; then
  ok "nginx 已存在:$(nginx -v 2>&1)"
else
  if [[ -f /etc/debian_version ]]; then
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nginx
  elif [[ -f /etc/redhat-release ]]; then
    dnf install -y nginx
  else
    die "未识别的 OS,请手动装 nginx 后重跑"
  fi
  systemctl enable --now nginx
  ok "nginx 已装 + 启动"
fi

# ---------- 2. 渲染配置 ----------
say "2/5 渲染 /etc/nginx/sites-available/finance.conf"
sed -e "s|__PORT__|${PORT}|g" \
    -e "s|__SERVER_NAME__|${SERVER_NAME}|g" \
    deploy/nginx-finance.conf.example > /etc/nginx/sites-available/finance.conf
chmod 644 /etc/nginx/sites-available/finance.conf
ok "/etc/nginx/sites-available/finance.conf 写入"

# ---------- 3. enable 站点 + 处理默认站点 ----------
say "3/5 enable finance.conf + 移除默认站点"
mkdir -p /etc/nginx/sites-enabled
ln -sf /etc/nginx/sites-available/finance.conf /etc/nginx/sites-enabled/finance.conf
ok "sites-enabled/finance.conf 软链就位"

if [[ -L /etc/nginx/sites-enabled/default ]]; then
  rm -f /etc/nginx/sites-enabled/default
  warn "已移除默认 80 站点(/etc/nginx/sites-enabled/default)避免端口冲突"
fi

# nginx 主配置 include 检查
if ! grep -qE '^\s*include\s+/etc/nginx/sites-enabled/' /etc/nginx/nginx.conf 2>/dev/null; then
  warn "/etc/nginx/nginx.conf 没 include sites-enabled/*,RHEL 默认目录是 conf.d"
  warn "改用 /etc/nginx/conf.d/finance.conf 兜底"
  cp /etc/nginx/sites-available/finance.conf /etc/nginx/conf.d/finance.conf
fi

# ---------- 4. 把 Spring 绑回 127.0.0.1 ----------
say "4/5 把 Spring 绑到 127.0.0.1(只允许 nginx 走 loopback 进)"
if grep -q '^SERVER_ADDRESS=' /etc/finance.env; then
  sed -i 's|^SERVER_ADDRESS=.*|SERVER_ADDRESS=127.0.0.1|' /etc/finance.env
  ok "SERVER_ADDRESS=127.0.0.1 已更新"
else
  echo "SERVER_ADDRESS=127.0.0.1" >> /etc/finance.env
  ok "SERVER_ADDRESS=127.0.0.1 已追加到 /etc/finance.env"
fi
systemctl restart finance
ok "finance 服务已 restart(现在外部访问 :${PORT} 会被拒,只 nginx 能走 127.0.0.1:${PORT})"

# ---------- 5. nginx -t + reload ----------
say "5/5 校验 + reload nginx"
nginx -t || die "nginx 配置语法错,看上面输出"
systemctl reload nginx
ok "nginx reload 完成"

# ---------- 健康检查 ----------
say "健康检查"
sleep 3
code_local=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${PORT}/health" || echo 000)
code_nginx=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1/health" || echo 000)
[[ "$code_local" == "200" ]] && ok "127.0.0.1:${PORT}/health = 200(后端直连)" || warn "后端 :${PORT} 异常 code=$code_local"
[[ "$code_nginx" == "200" ]] && ok "127.0.0.1:80/health = 200(nginx 反代)"   || warn "nginx :80 异常 code=$code_nginx"

echo
echo "${G}══════════════════════════════════════════${X}"
echo "${G}  nginx 反代部署完成                       ${X}"
echo "${G}══════════════════════════════════════════${X}"
echo
echo "外网现在应该可以这样访问(替换为公网 IP / 域名):"
echo "  http://<server-ip>/login"
echo "  http://<server-ip>/dashboard"
echo
echo "下一步可选(强烈建议):"
echo "  1. 防火墙关 :${PORT} 的公网入站,只留 :22 + :80(若装了 ufw):"
echo "       sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw enable"
echo "     或阿里云/腾讯云控制台的「安全组」里删 :${PORT} 的公网入站规则"
echo
echo "  2. 上 HTTPS(Let's Encrypt 免费,2 分钟):"
echo "       sudo apt-get install -y certbot python3-certbot-nginx"
echo "       sudo certbot --nginx -d your-domain.com"
echo "     certbot 会自动改 finance.conf 加 listen 443 ssl + 续签 cron"
echo
echo "排错:"
echo "  nginx 日志:  sudo tail -f /var/log/nginx/finance.{access,error}.log"
echo "  应用日志:    sudo journalctl -u finance -f"
