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
G=$'\033[32m'; R=$'\033[31m'; Y=$'\033[33m'; B=$'\033[1;36m'; X=$'\033[0m'

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
echo "${B}=== 2) 本地 mvn package(~10-15s)===${X}"
# 不加 -q,显示 [INFO] 阶段进度;过滤掉依赖下载 spam 让输出更短
set +e
mvn -B -DskipTests package 2>&1 \
  | grep -vE '^\[INFO\] (Downloading|Downloaded|Progress |  )'
MVN_RC=${PIPESTATUS[0]}
set -e
[[ $MVN_RC -eq 0 ]] || { echo "${R}✗${X} mvn package 失败(exit=$MVN_RC)" >&2; exit 1; }
test -f target/app.jar || { echo "✗ 构建失败,jar 缺失" >&2; exit 1; }
echo "${G}✓${X} jar: $(du -h target/app.jar | awk '{print $1}')  $(ls -la target/app.jar | awk '{print $9}')"

echo
echo "${B}=== 3) 创建远程 staging dir: ~/${STAGING}/ ===${X}"
ssh "$REMOTE" "mkdir -p ~/${STAGING}/{db/migration,deploy,icons}"

echo
echo "${B}=== 4) 推送交付物 ===${X}"

# 检测进度工具:rsync 优先(自带 --info=progress2 单行总进度);没有就回退 scp 自带进度条
if command -v rsync >/dev/null 2>&1; then
  USE_RSYNC=1
  # 老 rsync(3.0-)不支持 --info=progress2,降级到 --progress
  if rsync --info=help 2>&1 | grep -q progress2; then
    PROGRESS_FLAG="--info=progress2"
  else
    PROGRESS_FLAG="--progress"
  fi
else
  USE_RSYNC=0
fi

push() {
  # push <local-paths...> <remote-dir>
  local remote_dir="${@: -1}"
  local files=("${@:1:$#-1}")
  if [[ "$USE_RSYNC" == "1" ]]; then
    rsync -a $PROGRESS_FLAG -e ssh "${files[@]}" "$REMOTE:$remote_dir"
  else
    # scp 不带 -q,自带逐文件进度条
    scp "${files[@]}" "$REMOTE:$remote_dir"
  fi
}

echo "  ${B}[1/4]${X} jar(主体,~$(du -h target/app.jar | cut -f1)— 慢的一步)"
push target/app.jar "~/${STAGING}/app.jar"

echo
echo "  ${B}[2/4]${X} 数据库迁移($(ls db/migration/V*__*.sql | wc -l) 个 SQL + apply.sh)"
push db/apply.sh db/migration/V*__*.sql "~/${STAGING}/db/"
# rsync/scp 把 V*.sql 平铺进了 db/,需要挪进 db/migration/
ssh "$REMOTE" "cd ~/${STAGING}/db && mkdir -p migration && mv V*__*.sql migration/ 2>/dev/null || true"

echo
echo "  ${B}[3/4]${X} 部署模板 + 脚本(9 文件)"
push deploy/finance.service \
     deploy/finance.env.example \
     deploy/nginx-finance.conf.example \
     deploy/finance-backup.service \
     deploy/finance-backup.timer \
     deploy/backup.sh \
     deploy/init-prod.sh \
     deploy/deploy-prod.sh \
     deploy/nginx-setup.sh \
     "~/${STAGING}/deploy/"

if [[ -d icons ]] && ls icons/icon*.* >/dev/null 2>&1; then
  echo
  echo "  ${B}[4/4]${X} 图标源 PNG(可选,jar 内已含 16 张缩放好的)"
  push icons/icon*.* "~/${STAGING}/icons/"
else
  echo
  echo "  ${B}[4/4]${X} 图标源 PNG · 跳过(本地无 icons/ 目录)"
fi

echo
ssh "$REMOTE" "chmod +x ~/${STAGING}/db/apply.sh ~/${STAGING}/deploy/*.sh"
echo "${G}✓${X} 远端可执行权限设好"

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
