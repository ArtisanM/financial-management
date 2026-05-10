#!/usr/bin/env bash
# =========================================================
# deploy/push-to-prod.sh · 本地构建 + 推送 staging 到对方机器
#
# 用法(本地终端):
#   bash deploy/push-to-prod.sh user@prod.host
#   bash deploy/push-to-prod.sh user@prod.host /tmp/finance-deploy   # 自定义 staging 目录
#
# 这一步只把 jar + 迁移 SQL + systemd unit + nginx 模板等推到对方,
# 不动任何 prod 配置/服务。下一步登 prod 跑 init-prod.sh(首次)
# 或 deploy-prod.sh(后续迭代)。
# =========================================================
set -euo pipefail

REMOTE="${1:-}"
[[ -n "$REMOTE" ]] || { echo "用法: bash deploy/push-to-prod.sh user@host [staging-dir]" >&2; exit 1; }
STAGING="${2:-finance-deploy}"   # 默认相对 home,即 ~user/finance-deploy/

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
G=$'\033[32m'; B=$'\033[1;36m'; X=$'\033[0m'

echo "${B}=== 1) 预飞 ===${X}"
cd "$ROOT_DIR"
git status --porcelain | grep -q . && {
  echo "⚠ 本地 git tree 不干净,继续推送会用工作区内容(commit 哈希仅作参考):"
  git status --short
  read -p "继续? [y/N] " yn
  [[ "$yn" == "y" || "$yn" == "Y" ]] || exit 1
}
ssh -o ConnectTimeout=5 -o BatchMode=yes "$REMOTE" "echo ok" >/dev/null 2>&1 \
  || { echo "✗ ssh $REMOTE 不通,先 ssh-copy-id 或 ssh 进去一次" >&2; exit 1; }
echo "${G}✓${X} ssh $REMOTE 可达"
echo "${G}✓${X} commit: $(git rev-parse --short HEAD 2>/dev/null || echo 'no-git')"

echo
echo "${B}=== 2) 本地 mvn package ===${X}"
mvn -B -q -DskipTests package
test -f target/app.jar || { echo "✗ 构建失败" >&2; exit 1; }
echo "${G}✓${X} jar: $(ls -la target/app.jar | awk '{print $5,$9}')"

echo
echo "${B}=== 3) 创建远程 staging dir: ~/${STAGING}/ ===${X}"
ssh "$REMOTE" "mkdir -p ~/${STAGING}/{db/migration,deploy,icons}"

echo
echo "${B}=== 4) 推送交付物 ===${X}"
# 应用 jar
scp -q target/app.jar                        "$REMOTE:~/${STAGING}/app.jar"
# 数据库迁移 + apply
scp -q db/apply.sh                           "$REMOTE:~/${STAGING}/db/apply.sh"
scp -q db/migration/V*__*.sql                "$REMOTE:~/${STAGING}/db/migration/"
# 部署模板 + 脚本
scp -q deploy/finance.service                "$REMOTE:~/${STAGING}/deploy/"
scp -q deploy/finance.env.example            "$REMOTE:~/${STAGING}/deploy/"
scp -q deploy/nginx-finance.conf.example     "$REMOTE:~/${STAGING}/deploy/"
scp -q deploy/finance-backup.service         "$REMOTE:~/${STAGING}/deploy/"
scp -q deploy/finance-backup.timer           "$REMOTE:~/${STAGING}/deploy/"
scp -q deploy/backup.sh                      "$REMOTE:~/${STAGING}/deploy/"
scp -q deploy/init-prod.sh                   "$REMOTE:~/${STAGING}/deploy/"
scp -q deploy/deploy-prod.sh                 "$REMOTE:~/${STAGING}/deploy/"
# 资源(预设图标的源 PNG,以备对方需要重生成 — jar 内已含已缩放的 16 张)
[[ -d icons ]] && scp -q icons/icon*.* "$REMOTE:~/${STAGING}/icons/" || true

ssh "$REMOTE" "chmod +x ~/${STAGING}/db/apply.sh ~/${STAGING}/deploy/*.sh"
echo "${G}✓${X} 全部推送完毕"

echo
echo "${G}════════════════ 推送完成 ════════════════${X}"
echo "下一步在对方机器上执行:"
echo
echo "  ssh $REMOTE"
echo "  cd ~/${STAGING}"
echo "  sudo bash deploy/init-prod.sh        # 首次部署"
echo "  # 或者:"
echo "  sudo bash deploy/deploy-prod.sh      # 后续迭代(已 init 过)"
echo
