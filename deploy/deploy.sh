#!/usr/bin/env bash
# =========================================================
# deploy/deploy.sh · 家庭账房 v0.1 · 本地打包 + 拷贝到生产 + 重启
#
# 用法(本地终端跑):
#   ./deploy/deploy.sh                  # 默认:打包 + scp + 重启
#   ./deploy/deploy.sh --skip-build     # 跳过 mvn package
#   ./deploy/deploy.sh --no-restart     # 仅传文件,不重启
#
# 假设服务器已有:
#   - /opt/finance/                          ← 应用目录(属主 finance:finance)
#   - /etc/finance.env                       ← 配置(参考 deploy/finance.env.example)
#   - /etc/systemd/system/finance.service    ← systemd unit
#   - /var/finance/uploads/                  ← 上传目录
#   - mysql 服务,db user/pass 与 .env 对应
# =========================================================
set -euo pipefail

REMOTE="${REMOTE:?请设置 REMOTE=user@host}"
REMOTE_DIR="${REMOTE_DIR:-/opt/finance}"
SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
ROOT_DIR="$( cd "$SCRIPT_DIR/.." &> /dev/null && pwd )"
SKIP_BUILD=0
NO_RESTART=0

for arg in "$@"; do
    case "$arg" in
        --skip-build) SKIP_BUILD=1 ;;
        --no-restart) NO_RESTART=1 ;;
        *) echo "unknown arg: $arg" >&2; exit 1 ;;
    esac
done

echo "=== 1) 打包 ==="
if [ "$SKIP_BUILD" = "0" ]; then
    cd "$ROOT_DIR"
    mvn -B -q -DskipTests package
    echo "→ jar: $(ls -la target/app.jar | awk '{print $5,$9}')"
fi

echo "=== 2) 传 jar + 迁移 SQL ==="
ssh "$REMOTE" "mkdir -p $REMOTE_DIR/db/migration $REMOTE_DIR/logs"
scp "$ROOT_DIR/target/app.jar"            "$REMOTE:$REMOTE_DIR/app.jar.new"
scp "$ROOT_DIR/db/apply.sh"               "$REMOTE:$REMOTE_DIR/db/apply.sh"
scp "$ROOT_DIR/db/migration/"V*__*.sql    "$REMOTE:$REMOTE_DIR/db/migration/"

echo "=== 3) 数据库迁移(加载新 V*__) ==="
ssh "$REMOTE" "cd $REMOTE_DIR && set -a && source /etc/finance.env && set +a && ./db/apply.sh"

if [ "$NO_RESTART" = "1" ]; then
    echo "=== 跳过重启(--no-restart)·  ssh 后请手动 mv app.jar.new app.jar && systemctl restart finance ==="
    exit 0
fi

echo "=== 4) 重启 ==="
ssh "$REMOTE" "
    set -e
    sudo systemctl stop finance || true
    mv $REMOTE_DIR/app.jar.new $REMOTE_DIR/app.jar
    sudo chown finance:finance $REMOTE_DIR/app.jar
    sudo systemctl start finance
    sleep 3
    sudo systemctl status finance --no-pager | head -10
"

echo "=== 5) 烟测 ==="
ssh "$REMOTE" "curl -sf http://127.0.0.1:8080/login >/dev/null && echo '✓ 应用响应正常' || echo '✗ 应用无响应'"

echo "=== 完成 ==="
