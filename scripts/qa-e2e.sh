#!/usr/bin/env bash
# v0.2 封版 · 端到端数据真值校验
BASE="${BASE:-http://localhost:20000}"
COOKIE="/tmp/finance-e2e.txt"
PASS=0; FAIL=0

. /etc/finance.env
SQL() { mysql -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASS" finance -BNe "$1" 2>/dev/null; }

ok()   { echo -e "\033[32m✓\033[0m $1"; PASS=$((PASS+1)); }
bad()  { echo -e "\033[31m✗\033[0m $1  (want=$2  got=$3)"; FAIL=$((FAIL+1)); }
section() { echo; echo -e "\033[1;36m── $1 ──\033[0m"; }

assert_eq() {
  if [[ "$2" == "$3" ]]; then ok "$1 = $3"; else bad "$1" "$2" "$3"; fi
}

# === 0. 重置 + 开 2026-05 ===
section "0 · 重置 DB + 开 2026-05"
SQL "
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE cash_flow; TRUNCATE transfer; TRUNCATE period_snapshot;
TRUNCATE snapshot_todo; TRUNCATE period_member_completion; TRUNCATE period_reopen_log;
TRUNCATE metrics_recompute_log; TRUNCATE audit_log; TRUNCATE backup_log; TRUNCATE fx_rate;
TRUNCATE period;
SET FOREIGN_KEY_CHECKS=1;
INSERT INTO period (family_id, period_type, period_start, period_end, status)
VALUES (1, 'MONTHLY', '2026-05-01', '2026-05-31', 'OPEN');
INSERT INTO snapshot_todo (period_id, account_id, assigned_member_id, status)
SELECT p.id, a.id, a.primary_owner_member_id, 'PENDING'
  FROM period p JOIN account a ON a.family_id=p.family_id
 WHERE p.family_id=1 AND p.period_start='2026-05-01' AND a.archived_at IS NULL;
" >/dev/null
sudo -n systemctl restart finance
# wait until /health returns 200 (服务可能 5-10s 才完全启动)
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/health")
  if [[ "$code" == "200" ]]; then break; fi
  sleep 1
done
sleep 1  # 再多等 1s 让 spring 完成 lazy init
PERIOD05=$(SQL "SELECT id FROM period WHERE period_start='2026-05-01' AND family_id=1")
assert_eq "2026-05 OPEN id" "1" "$([ -n "$PERIOD05" ] && echo 1 || echo 0)"

# === 0.5 登录 ===
rm -f "$COOKIE"
TOKEN=$(curl -s -c "$COOKIE" "$BASE/login" | grep -oE 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="\([^"]*\)".*/\1/')
curl -s -b "$COOKIE" -c "$COOKIE" -X POST --data-urlencode "_csrf=$TOKEN" --data-urlencode "username=diwa" --data-urlencode "password=demo1234" "$BASE/login" -o /dev/null
curl -s -b "$COOKIE" -c "$COOKIE" "$BASE/dashboard" -o /dev/null
XSRF=$(awk '$6=="XSRF-TOKEN" {print $7}' "$COOKIE")

post_balance() {
  curl -s -b "$COOKIE" -X POST -H "X-XSRF-TOKEN: $XSRF" -H "HX-Request: true" \
    --data-urlencode "newBalance=$2" --data-urlencode "periodId=$3" \
    "$BASE/entry/$1/balance" -o /dev/null -w "%{http_code}"
}
post_cf() {
  curl -s -b "$COOKIE" -X POST -H "X-XSRF-TOKEN: $XSRF" -H "HX-Request: true" \
    --data-urlencode "kind=$2" --data-urlencode "categoryCode=$3" \
    --data-urlencode "amount=$4" --data-urlencode "periodId=$5" \
    "$BASE/entry/$1/cash-flow" -o /dev/null -w "%{http_code}"
}
post_transfer() {
  curl -s -b "$COOKIE" -X POST -H "X-XSRF-TOKEN: $XSRF" -H "HX-Request: true" \
    --data-urlencode "toAccountId=$2" --data-urlencode "amount=$3" --data-urlencode "periodId=$4" \
    "$BASE/entry/$1/transfer" -o /dev/null -w "%{http_code}"
}

# === 1. 填 5 个账户 2026-05 期末余额 ===
section "1 · 填 5 个账户 2026-05 期末余额"
assert_eq "POST acct=1 ¥10000"    "200" "$(post_balance 1 10000 "$PERIOD05")"
assert_eq "POST acct=2 ¥5000"     "200" "$(post_balance 2 5000 "$PERIOD05")"
assert_eq "POST acct=3 ¥50000"    "200" "$(post_balance 3 50000 "$PERIOD05")"
assert_eq "POST acct=9 ¥30000"    "200" "$(post_balance 9 30000 "$PERIOD05")"
assert_eq "POST acct=11 ¥-200000" "200" "$(post_balance 11 -200000 "$PERIOD05")"

# === 2. 现金流 + 转账 ===
section "2 · 收入 / 支出 / 转账"
assert_eq "INCOME acct=1 ¥3000"  "200" "$(post_cf 1 INCOME salary 3000 "$PERIOD05")"
assert_eq "EXPENSE acct=1 ¥500"  "200" "$(post_cf 1 EXPENSE consumption 500 "$PERIOD05")"
assert_eq "Transfer 1→2 ¥2000"   "200" "$(post_transfer 1 2 2000 "$PERIOD05")"

# === 3. DB 真值 ===
section "3 · DB 真值"
assert_eq "acct=1(10000+3000-500-2000)" "10500.00"   "$(SQL "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD05 AND account_id=1")"
assert_eq "acct=2(5000+2000)"            "7000.00"   "$(SQL "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD05 AND account_id=2")"
assert_eq "acct=3(无变化)"                "50000.00"  "$(SQL "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD05 AND account_id=3")"
assert_eq "acct=9 WEALTH"                "30000.00"  "$(SQL "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD05 AND account_id=9")"
assert_eq "acct=11 LOAN"                 "-200000.00" "$(SQL "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD05 AND account_id=11")"
assert_eq "cf 行数"                      "2"          "$(SQL "SELECT COUNT(*) FROM cash_flow WHERE period_id=$PERIOD05")"
assert_eq "transfer 行数"                "1"          "$(SQL "SELECT COUNT(*) FROM transfer WHERE period_id=$PERIOD05")"
assert_eq "todo DONE 行数"               "5"          "$(SQL "SELECT COUNT(*) FROM snapshot_todo WHERE period_id=$PERIOD05 AND status='DONE'")"

# === 4. /dashboard KPI ===
section "4 · /dashboard KPI"
TMP=/tmp/e2e-d.html; rm -f "$TMP"
curl -s -b "$COOKIE" -o "$TMP" "$BASE/dashboard?range=1Y&currency=CNY"
extract_kpi() {
  python3 - "$1" "$2" <<'PYEOF'
import sys, re
with open(sys.argv[1]) as f: html = f.read()
m = re.search(r'kpi-eyebrow[^>]*>' + re.escape(sys.argv[2]) + r'</span>\s*<div[^>]*>([^<]+)', html)
print(m.group(1).strip() if m else 'NOT_FOUND')
PYEOF
}
assert_eq "净资产(应 -102500)"    "¥-102,500"  "$(extract_kpi "$TMP" 净资产)"
assert_eq "总资产(应 97500)"      "¥97,500"    "$(extract_kpi "$TMP" 总资产)"
assert_eq "总负债(应 200000)"     "¥200,000"   "$(extract_kpi "$TMP" 总负债)"
assert_eq "紧急储备(应 35.0 月)"   "35.0 月"   "$(extract_kpi "$TMP" 紧急储备)"
assert_eq "负债率(应 205.1%)"      "205.1%"    "$(extract_kpi "$TMP" 负债率)"

# === 5. /checkup 全家 KPI ===
section "5 · /checkup 全家 KPI"
TMP=/tmp/e2e-c.html; rm -f "$TMP"
curl -s -b "$COOKIE" -o "$TMP" "$BASE/checkup"
# /checkup 用 #numbers.formatDecimal(x, 1, 0) 没千分位
assert_eq "checkup 净资产" "¥-102500" "$(extract_kpi "$TMP" 净资产)"
assert_eq "checkup 总资产" "¥97500"   "$(extract_kpi "$TMP" 总资产)"
assert_eq "checkup 总负债" "¥200000"  "$(extract_kpi "$TMP" 总负债)"

# === 6. /accounts/1 详情 ===
section "6 · /accounts/1 详情(招行储蓄卡 应 ¥10,500.00)"
TMP=/tmp/e2e-a1.html; rm -f "$TMP"
curl -s -b "$COOKIE" -o "$TMP" "$BASE/accounts/1"
got=$(python3 - "$TMP" <<'PYEOF'
import sys, re
with open(sys.argv[1]) as f: html = f.read()
m = re.search(r'kpi-eyebrow[^>]*>当前本期余额</div>\s*<div[^>]*>([^<]+)', html, re.S)
print(m.group(1).strip() if m else 'NOT_FOUND')
PYEOF
)
assert_eq "acct=1 详情显示余额" "¥10500.00" "$got"

# === 7. 关 2026-05 + 开 2026-06 ===
section "7 · 关 2026-05 + 开 2026-06"
# 强制关闭(管理员路径)— Spring 表单 POST,需 _csrf form 字段
NEW_TOKEN=$(curl -s -b "$COOKIE" "$BASE/admin/periods" | grep -oE 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="\([^"]*\)".*/\1/')
code=$(curl -s -b "$COOKIE" -X POST \
  --data-urlencode "_csrf=$NEW_TOKEN" \
  "$BASE/admin/periods/$PERIOD05/force-close" -o /dev/null -w "%{http_code}")
assert_eq "force-close 2026-05" "302" "$code"
assert_eq "2026-05 status" "CLOSED" "$(SQL "SELECT status FROM period WHERE id=$PERIOD05")"

# 开 2026-06
code=$(curl -s -b "$COOKIE" -X POST \
  --data-urlencode "_csrf=$NEW_TOKEN" \
  "$BASE/admin/periods/open-next" -o /dev/null -w "%{http_code}")
assert_eq "open-next" "302" "$code"
PERIOD06=$(SQL "SELECT id FROM period WHERE period_start='2026-06-01' AND family_id=1")
assert_eq "2026-06 已建" "1" "$([ -n "$PERIOD06" ] && echo 1 || echo 0)"
assert_eq "06 期 acct=1 自动延续上期末 ¥10500" "10500.00" "$(SQL "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD06 AND account_id=1")"

# === 8. 06 期 +¥4000 工资 ===
section "8 · 06 期 +¥4000 工资 → ¥14500"
# XSRF cookie 可能在前面 POST 后变了,刷新一下
curl -s -b "$COOKIE" -c "$COOKIE" "$BASE/dashboard" -o /dev/null
XSRF=$(awk '$6=="XSRF-TOKEN" {print $7}' "$COOKIE")
assert_eq "INCOME acct=1 ¥4000" "200" "$(post_cf 1 INCOME salary 4000 "$PERIOD06")"
assert_eq "06 acct=1 余额(10500+4000)" "14500.00" "$(SQL "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD06 AND account_id=1")"

# === 9. 06 期 dashboard 较上期 ===
section "9 · 06 期 dashboard 较上期"
TMP=/tmp/e2e-d2.html; rm -f "$TMP"
curl -s -b "$COOKIE" -o "$TMP" "$BASE/dashboard?range=1Y&currency=CNY"
delta=$(python3 - "$TMP" <<'PYEOF'
import sys, re
with open(sys.argv[1]) as f: html = f.read()
m = re.search(r'kpi-eyebrow[^>]*>净资产</span>\s*<div[^>]*>[^<]+</div>\s*<div[^>]*kpi-delta[^>]*>([^<]+)', html, re.S)
print(m.group(1).strip() if m else 'NOT_FOUND')
PYEOF
)
assert_eq "净资产 delta(+4000 收入)" "+¥4,000" "$delta"

# === 10. /accounts/1 较上期 ===
section "10 · /accounts/1 详情 较上期"
TMP=/tmp/e2e-a2.html; rm -f "$TMP"
curl -s -b "$COOKIE" -o "$TMP" "$BASE/accounts/1"
delta_pct=$(python3 - "$TMP" <<'PYEOF'
import sys, re
with open(sys.argv[1]) as f: html = f.read()
m = re.search(r'kpi-eyebrow[^>]*>较上期</div>\s*<div[^>]*>([^<]+)', html, re.S)
print(m.group(1).strip() if m else 'NOT_FOUND')
PYEOF
)
# 14500 vs 10500 → +4000/10500 = 38.1%
assert_eq "acct=1 较上期 +38.1%" "+38.1%" "$delta_pct"

# === 11. /checkup 家庭 XIRR 已计算 ===
section "11 · /checkup 家庭 XIRR(2 期后应可计算)"
TMP=/tmp/e2e-cx.html; rm -f "$TMP"
curl -s -b "$COOKIE" -o "$TMP" "$BASE/checkup"
xirr=$(extract_kpi "$TMP" 家庭年化)
[[ "$xirr" != "—" && "$xirr" != "NOT_FOUND" && -n "$xirr" ]] \
  && ok "家庭年化 XIRR 已计算 = $xirr" \
  || bad "家庭 XIRR" "非空非—" "$xirr"

# === 总结 ===
echo
echo "═══════════════════════════════════════"
echo " 总结: PASS=$PASS  FAIL=$FAIL"
echo "═══════════════════════════════════════"
exit $((FAIL > 0 ? 1 : 0))
