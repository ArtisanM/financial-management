#!/usr/bin/env bash
# =========================================================
# deploy/backup.sh · 家庭账房 v0.1 · 周日 03:00 mysqldump + 上传目录
#
# 由 systemd timer finance-backup.timer 触发(详见 finance-backup.{service,timer})
# 也可手动跑:
#   sudo -u finance /opt/finance/deploy/backup.sh
#
# 行为:
#   1. mysqldump 整库 → gzip 到 /var/backup/finance/dump-YYYY-MM-DD.sql.gz
#   2. tar uploads/ → gzip 到 /var/backup/finance/uploads-YYYY-MM-DD.tar.gz
#   3. 滚动删除最旧:保留最近 8 周(56 天)
#   4. (可选)异地推到 OSS,$REMOTE_TARGET 存在时
#   5. 写入 backup_log 表(SUCCESS / FAILED + 大小 + 错误)
# =========================================================
set -euo pipefail

# 配置(也可来自环境变量 / /etc/finance.env)
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:?DB_USER 未设置}"
DB_PASS="${DB_PASS:?DB_PASS 未设置}"
DB_NAME="${DB_NAME:?DB_NAME 未设置}"

BACKUP_DIR="${BACKUP_DIR:-/var/backup/finance}"
UPLOAD_DIR="${UPLOAD_ROOT:-/var/finance/uploads}"
RETENTION_DAYS="${RETENTION_DAYS:-56}"
REMOTE_TARGET="${REMOTE_TARGET:-}"     # 例:oss://family-finance/  (留空 = 仅本地)
FAMILY_ID="${FAMILY_ID:-1}"

START_TS=$(date '+%Y-%m-%d %H:%M:%S')
DATE_TAG=$(date '+%Y-%m-%d')
DUMP="$BACKUP_DIR/dump-$DATE_TAG.sql.gz"
UPLOAD_BUNDLE="$BACKUP_DIR/uploads-$DATE_TAG.tar.gz"

mkdir -p "$BACKUP_DIR"

mysql_args=( -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" )

write_log() {
    local status="$1"  # SUCCESS / FAILED
    local size="$2"
    local err="$3"
    local end_ts; end_ts=$(date '+%Y-%m-%d %H:%M:%S')
    mysql "${mysql_args[@]}" "$DB_NAME" --default-character-set=utf8mb4 <<SQL || true
INSERT INTO backup_log (
    family_id, kind, status, size_bytes,
    location_local, location_remote, error_message,
    started_at, completed_at
) VALUES (
    $FAMILY_ID, 'WEEKLY', '$status',
    $( [ -n "$size" ] && echo "$size" || echo "NULL" ),
    '$DUMP',
    $( [ -n "$REMOTE_TARGET" ] && echo "'$REMOTE_TARGET$( basename "$DUMP" )'" || echo "NULL" ),
    $( [ -n "$err" ] && echo "'$( echo "$err" | sed "s/'/''/g" )'" || echo "NULL" ),
    '$START_TS', '$end_ts'
);
SQL
}

trap 'write_log FAILED "" "$( tail -1 "$LOG_FILE" 2>/dev/null )"; exit 1' ERR

LOG_FILE=$(mktemp)

{
    echo "=== mysqldump $DB_NAME → $DUMP ==="
    mysqldump "${mysql_args[@]}" \
        --single-transaction --routines --triggers --quick \
        --default-character-set=utf8mb4 \
        "$DB_NAME" | gzip -9 > "$DUMP"

    echo "=== uploads → $UPLOAD_BUNDLE ==="
    if [ -d "$UPLOAD_DIR" ]; then
        tar -czf "$UPLOAD_BUNDLE" -C "$UPLOAD_DIR" .
    else
        echo "(uploads 目录不存在,跳过)"
    fi

    if [ -n "$REMOTE_TARGET" ]; then
        echo "=== 异地推送 ==="
        if command -v ossutil >/dev/null; then
            ossutil cp "$DUMP"           "$REMOTE_TARGET" --update
            [ -f "$UPLOAD_BUNDLE" ] && ossutil cp "$UPLOAD_BUNDLE" "$REMOTE_TARGET" --update
        else
            echo "(ossutil 未安装,跳过异地推送)"
        fi
    fi

    echo "=== 清理 > $RETENTION_DAYS 天的旧备份 ==="
    find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'dump-*.sql.gz' -o -name 'uploads-*.tar.gz' \) \
        -mtime +"$RETENTION_DAYS" -print -delete

} >"$LOG_FILE" 2>&1

cat "$LOG_FILE"
SIZE=$(stat -c%s "$DUMP" 2>/dev/null || echo "")
write_log "SUCCESS" "$SIZE" ""
echo "✓ backup OK · dump=$SIZE bytes"
