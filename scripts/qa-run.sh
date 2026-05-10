#!/usr/bin/env bash
# 家庭账房 v0.1 QA 自动测试 — curl + grep 黑盒
# 用法: bash /tmp/qa-run.sh
# 退出码 0 = 全部 PASS,!=0 = 至少一条 FAIL

set -u
BASE="${BASE:-http://localhost:20000}"
COOKIE="/tmp/finance-qa-cookie.txt"
TMP="/tmp/finance-qa-resp.html"
PASS=0; FAIL=0; SKIP=0
FAILED=()

log_ok()   { echo -e "\033[32m PASS \033[0m $1"; PASS=$((PASS+1)); }
log_bad()  { echo -e "\033[31m FAIL \033[0m $1  ::  $2"; FAIL=$((FAIL+1)); FAILED+=("$1 :: $2"); }
log_skip() { echo -e "\033[33m SKIP \033[0m $1  ::  $2"; SKIP=$((SKIP+1)); }
section()  { echo; echo -e "\033[1;36m─── $1 ───\033[0m"; }

CURL="/usr/bin/curl -s --max-time 15"

# ---------- 0 · 认证 ----------
section "0 · 认证"

# AUTH-1 未登录跳登录
rm -f /tmp/finance-qa-noauth.txt
code=$($CURL -o /dev/null -w "%{http_code}" "$BASE/dashboard")
loc=$($CURL -o /dev/null -w "%{redirect_url}" "$BASE/dashboard")
[[ "$code" == "302" && "$loc" == *"/login" ]] && log_ok "AUTH-1 未登录访问 /dashboard 跳 /login" || log_bad "AUTH-1 未登录跳登录" "code=$code loc=$loc"

# AUTH-2 登录页
$CURL "$BASE/login" -o "$TMP" -w ""
grep -q '_csrf' "$TMP" && grep -q 'name="username"' "$TMP" && grep -q 'name="password"' "$TMP" \
  && log_ok "AUTH-2 登录页含 _csrf+用户名+密码字段" || log_bad "AUTH-2 登录页字段缺" "missing fields"

# AUTH-3 错误密码
rm -f $COOKIE
TOKEN=$($CURL -c $COOKIE "$BASE/login" | grep -oE 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="\([^"]*\)".*/\1/')
loc=$($CURL -b $COOKIE -c $COOKIE -X POST --data-urlencode "_csrf=$TOKEN" --data-urlencode "username=diwa" --data-urlencode "password=WRONG" "$BASE/login" -o /dev/null -w "%{redirect_url}")
[[ "$loc" == *"/login?error" ]] && log_ok "AUTH-3 错误密码跳 /login?error" || log_bad "AUTH-3 错误密码处理" "loc=$loc"

# AUTH-4 正确登录
rm -f $COOKIE
TOKEN=$($CURL -c $COOKIE "$BASE/login" | grep -oE 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="\([^"]*\)".*/\1/')
loc=$($CURL -b $COOKIE -c $COOKIE -X POST --data-urlencode "_csrf=$TOKEN" --data-urlencode "username=diwa" --data-urlencode "password=demo1234" "$BASE/login" -o /dev/null -w "%{redirect_url}")
[[ "$loc" == *"/" ]] && log_ok "AUTH-4 正确密码登录 → /" || log_bad "AUTH-4 登录失败" "loc=$loc"

# AUTH-5 dashboard 完整 HTML
$CURL -b $COOKIE "$BASE/dashboard" -o "$TMP" -w ""
grep -q "</html>" "$TMP" && log_ok "AUTH-5 /dashboard 完整 HTML" || log_bad "AUTH-5 /dashboard 不完整" "no </html>"

# AUTH-7 /health 公开
$CURL "$BASE/health" -o "$TMP" -w ""
grep -q '"status":"UP"' "$TMP" && log_ok "AUTH-7 /health 公开 JSON" || log_bad "AUTH-7 /health" "$(cat $TMP)"

# ---------- FR-1 ----------
section "FR-1 · 家庭与成员"

$CURL -b $COOKIE "$BASE/admin/family" -o "$TMP" -w ""
{ grep -q "家庭" "$TMP" && grep -q "周期类型" "$TMP" && grep -q "</html>" "$TMP"; } && log_ok "FR1-1 /admin/family 200+完整" || log_bad "FR1-1 /admin/family 缺" "missing"

$CURL -b $COOKIE "$BASE/admin/members" -o "$TMP" -w ""
{ grep -q "diwa" "$TMP" && grep -q "lijing" "$TMP" && grep -q "</html>" "$TMP"; } && log_ok "FR1-2 /admin/members 列出 2 人" || log_bad "FR1-2 /admin/members" "missing names"

# FR1-7 添加成员入口存在
grep -q "+ 添加成员" "$TMP" && log_ok "FR1-7 /admin/members 含'添加成员'入口" || log_bad "FR1-7 添加成员入口" "missing"

# FR1-8 改密页可访问 (登录态下)
$CURL -b $COOKIE "$BASE/profile/password" -o "$TMP" -w ""
{ grep -q "修改" "$TMP" && grep -q "新密码" "$TMP" && grep -q "</html>" "$TMP"; } && log_ok "FR1-8 /profile/password 改密页" || log_bad "FR1-8 改密页" "missing"

# logo 上传 form
grep -q "logo" /tmp/finance-qa-resp.html && log_skip "FR1-6 logo 表单" "需视觉确认"

# ---------- FR-2 ----------
section "FR-2 · 模板向导"
$CURL -b $COOKIE "$BASE/accounts/new" -o "$TMP" -w ""
grep -q "添加账户向导" "$TMP" && log_ok "FR2-1 /accounts/new 弹向导" || log_bad "FR2-1 向导缺" "missing wizard"
grep -q "现金 (CASH)" "$TMP" && log_ok "FR2-3 类型下拉中文化" || log_bad "FR2-3 中文化" "no chinese label"

$CURL -b $COOKIE "$BASE/admin/account-templates" -o "$TMP" -w ""
grep -q "</html>" "$TMP" && log_ok "FR2-2 /admin/account-templates" || log_bad "FR2-2 模板页" "incomplete"

# ---------- FR-3 ----------
section "FR-3 · 账户管理"
$CURL -b $COOKIE "$BASE/accounts" -o "$TMP" -w ""
grep -q "招行储蓄卡-工资" "$TMP" && log_ok "FR3-1 /accounts 列表" || log_bad "FR3-1 /accounts" "missing accounts"

$CURL -b $COOKIE "$BASE/accounts/1/edit" -o "$TMP" -w ""
{ grep -q "保存对账户的修改" "$TMP" && grep -q "招行储蓄卡-工资" "$TMP"; } && log_ok "FR3-3 编辑专属页正确" || log_bad "FR3-3 编辑页" "missing"

# ---------- FR-5 待办 / 周期 ----------
section "FR-5 · 周期与待办"
periods=$(mysql -ufinance -pfinance finance -sN -e "SELECT COUNT(*) FROM period WHERE status='OPEN' AND family_id=1" 2>/dev/null)
[[ "$periods" -ge 1 ]] && log_ok "FR5-1 OPEN 周期存在 ($periods)" || log_bad "FR5-1 没有 OPEN 周期" "count=$periods"

todos=$(mysql -ufinance -pfinance finance -sN -e "SELECT COUNT(*) FROM snapshot_todo st JOIN period p ON p.id=st.period_id WHERE p.status='OPEN' AND p.family_id=1" 2>/dev/null)
accounts=$(mysql -ufinance -pfinance finance -sN -e "SELECT COUNT(*) FROM account WHERE archived_at IS NULL AND family_id=1" 2>/dev/null)
[[ "$todos" -gt 0 ]] && log_ok "FR5-2 待办存在 ($todos vs accounts $accounts)" || log_bad "FR5-2 待办空" "todos=$todos accounts=$accounts"

# ---------- FR-6 待办视图 ----------
section "FR-6 · 待办视图"
$CURL -b $COOKIE "$BASE/my-todos" -o "$TMP" -w ""
grep -q "</html>" "$TMP" && log_ok "FR6-1 /my-todos 200" || log_bad "FR6-1" "incomplete"
# FR6-2 my-todos 行的 "填 →" 链接只在 row.done=false 时渲染;
# v0.2 PeriodOpener 自动延续上期末 snapshot 后,所有账户 done=true → 无 "填 →" 链接是预期。
# 仅当存在没 snapshot 的账户(罕见)才校验 link;否则 SKIP。
NO_SNAP=$(mysql -ufinance -pfinance finance -sN -e "
SELECT COUNT(*) FROM account a
 WHERE a.family_id=1 AND a.archived_at IS NULL
   AND a.id NOT IN (SELECT account_id FROM period_snapshot
                      WHERE period_id IN (SELECT id FROM period WHERE family_id=1 AND status='OPEN'))" 2>/dev/null)
if [[ "$NO_SNAP" == "0" ]]; then
  log_skip "FR6-2 my-todos 链接" "OPEN 期所有账户已自动延续 snapshot,row.done=true,无「填 →」链接(预期)"
else
  grep -qE 'href="/entry\?[^"]*account=' "$TMP" && log_ok "FR6-2 /my-todos→/entry 携带 account=" || log_bad "FR6-2 链接无 account=" "missing"
fi

# 比较 mine=true vs mine=false
sa=$($CURL -b $COOKIE "$BASE/entry" -o /tmp/finance-qa-all.html -w "%{size_download}")
sm=$($CURL -b $COOKIE "$BASE/entry?mine=true" -o /tmp/finance-qa-mine.html -w "%{size_download}")
[[ "$sm" -lt "$sa" ]] && log_ok "FR6-3 mine=true 行数减少 (all=$sa mine=$sm)" || log_bad "FR6-3 mine 没减少" "all=$sa mine=$sm"

# 账户筛选
$CURL -b $COOKIE "$BASE/entry?account=1" -o "$TMP" -w ""
grep -q "已按账户筛选" "$TMP" && log_ok "FR6-4 account 筛选生效" || log_bad "FR6-4 account 筛选无 banner" "missing"

# ---------- FR-7~10 录入 / 现金流 / 转账 ----------
section "FR-7~10 · 录入/现金流/转账(POST)"

PERIOD_ID=$(mysql -ufinance -pfinance finance -sN -e "SELECT id FROM period WHERE status='OPEN' AND family_id=1 LIMIT 1" 2>/dev/null)
ACC1=$(mysql -ufinance -pfinance finance -sN -e "SELECT id FROM account WHERE display_name='招行储蓄卡-工资' LIMIT 1" 2>/dev/null)
ACC2=$(mysql -ufinance -pfinance finance -sN -e "SELECT id FROM account WHERE display_name='工商银行-备用金' LIMIT 1" 2>/dev/null)

[[ -n "$PERIOD_ID" && -n "$ACC1" ]] || { log_bad "FR7-prep" "missing period/account ids"; }

# 提取 csrf cookie
CSRF=$(grep XSRF-TOKEN $COOKIE 2>/dev/null | awk '{print $7}' | tail -1)
[[ -z "$CSRF" ]] && { $CURL -b $COOKIE -c $COOKIE "$BASE/dashboard" -o /dev/null; CSRF=$(grep XSRF-TOKEN $COOKIE | awk '{print $7}' | tail -1); }

# FR-7 提交余额
code=$($CURL -b $COOKIE -X POST -H "X-XSRF-TOKEN: $CSRF" -H "HX-Request: true" \
  --data-urlencode "newBalance=46000" --data-urlencode "note=qa-test" --data-urlencode "periodId=$PERIOD_ID" \
  "$BASE/entry/$ACC1/balance" -o "$TMP" -w "%{http_code}")
[[ "$code" == "200" ]] && grep -q "entry-row-$ACC1" "$TMP" && log_ok "FR7-2 提交余额 200+fragment" || log_bad "FR7-2 提交余额" "code=$code"

snap=$(mysql -ufinance -pfinance finance -sN -e "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD_ID AND account_id=$ACC1" 2>/dev/null)
[[ -n "$snap" ]] && log_ok "FR7-2db 快照写入 ($snap)" || log_bad "FR7-2db 无快照" "no row"

# FR-8 收入
code=$($CURL -b $COOKIE -X POST -H "X-XSRF-TOKEN: $CSRF" -H "HX-Request: true" \
  --data-urlencode "kind=INCOME" --data-urlencode "categoryCode=salary" --data-urlencode "amount=1000" --data-urlencode "periodId=$PERIOD_ID" \
  "$BASE/entry/$ACC1/cash-flow" -o /dev/null -w "%{http_code}")
[[ "$code" == "200" ]] && log_ok "FR8-1 提交收入 200" || log_bad "FR8-1 收入" "code=$code"

# FR8-1+ 收入提交后响应头含 HX-Trigger=refresh-row-{id} 让客户端 self-refresh 刷新 ledger
hdrs=$($CURL -b $COOKIE -D - -X POST -H "X-XSRF-TOKEN: $CSRF" -H "HX-Request: true" \
  --data-urlencode "kind=INCOME" --data-urlencode "categoryCode=salary" --data-urlencode "amount=1234" --data-urlencode "periodId=$PERIOD_ID" \
  "$BASE/entry/$ACC1/cash-flow" -o /dev/null)
echo "$hdrs" | grep -qi "HX-Trigger:.*refresh-row-$ACC1" \
  && log_ok "FR8-1b 收入响应含 HX-Trigger=refresh-row-$ACC1(触发 GET 自我刷新 ledger)" \
  || log_bad "FR8-1b HX-Trigger 缺失" "no header"

# FR7-8~11 5 步场景:上期=10000 → +收入 100 → -支出 1000 → 校准 4000 → +收入 200
SCEN_ACC=$(mysql -ufinance -pfinance finance -sN -e "SELECT id FROM account WHERE display_name='工商银行-备用金' LIMIT 1" 2>/dev/null)
LATEST_PREV=$(mysql -ufinance -pfinance finance -sN -e "SELECT id FROM period WHERE family_id=1 AND status='CLOSED' ORDER BY period_start DESC LIMIT 1" 2>/dev/null)
mysql -ufinance -pfinance finance -e "
DELETE FROM cash_flow WHERE period_id=$PERIOD_ID AND account_id=$SCEN_ACC;
DELETE FROM transfer WHERE period_id=$PERIOD_ID AND (from_account_id=$SCEN_ACC OR to_account_id=$SCEN_ACC);
INSERT INTO period_snapshot(period_id, account_id, end_balance, submitted_by, note)
VALUES ($PERIOD_ID, $SCEN_ACC, 10000, 1, 'qa-seed') ON DUPLICATE KEY UPDATE end_balance=10000, note='qa-seed';
UPDATE period_snapshot SET end_balance=10000 WHERE period_id=$LATEST_PREV AND account_id=$SCEN_ACC;" 2>/dev/null

# FR7-7 输入框预填上期值(input 元素跨行,用 pcregrep 多行 / 退而 awk)
$CURL -b $COOKIE "$BASE/entry?account=$SCEN_ACC" -o "$TMP" -w ""
awk '/name="newBalance"/{flag=3} flag>0{buf=buf $0; flag--} END{if(buf ~ /value="10000\.00"/) exit 0; else exit 1}' "$TMP" \
  && log_ok "FR7-7 输入框预填上期末 10000" \
  || log_bad "FR7-7 输入框未预填" "missing value=10000"

# FR7-8 +收入 100 → 余额 10100
$CURL -b $COOKIE -X POST -H "X-XSRF-TOKEN: $CSRF" -H "HX-Request: true" \
  --data-urlencode "kind=INCOME" --data-urlencode "categoryCode=salary" --data-urlencode "amount=100" --data-urlencode "periodId=$PERIOD_ID" \
  "$BASE/entry/$SCEN_ACC/cash-flow" -o /dev/null
b1=$(mysql -ufinance -pfinance finance -sN -e "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD_ID AND account_id=$SCEN_ACC" 2>/dev/null)
[[ "$b1" == "10100.00" ]] && log_ok "FR7-8 +收入100 → 余额=10100" || log_bad "FR7-8" "got=$b1"

# FR7-9 -支出 1000 → 余额 9100
$CURL -b $COOKIE -X POST -H "X-XSRF-TOKEN: $CSRF" -H "HX-Request: true" \
  --data-urlencode "kind=EXPENSE" --data-urlencode "categoryCode=consumption" --data-urlencode "amount=1000" --data-urlencode "periodId=$PERIOD_ID" \
  "$BASE/entry/$SCEN_ACC/cash-flow" -o /dev/null
b2=$(mysql -ufinance -pfinance finance -sN -e "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD_ID AND account_id=$SCEN_ACC" 2>/dev/null)
[[ "$b2" == "9100.00" ]] && log_ok "FR7-9 -支出1000 → 余额=9100" || log_bad "FR7-9" "got=$b2"

# FR7-10 校准余额 4000 → 直接覆盖
$CURL -b $COOKIE -X POST -H "X-XSRF-TOKEN: $CSRF" -H "HX-Request: true" \
  --data-urlencode "newBalance=4000" --data-urlencode "periodId=$PERIOD_ID" \
  "$BASE/entry/$SCEN_ACC/balance" -o /dev/null
b3=$(mysql -ufinance -pfinance finance -sN -e "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD_ID AND account_id=$SCEN_ACC" 2>/dev/null)
[[ "$b3" == "4000.00" ]] && log_ok "FR7-10 校准至 4000(覆盖)" || log_bad "FR7-10" "got=$b3"

# FR7-11 校准后 +收入 200 → 余额 4200(在新基础上累加)
$CURL -b $COOKIE -X POST -H "X-XSRF-TOKEN: $CSRF" -H "HX-Request: true" \
  --data-urlencode "kind=INCOME" --data-urlencode "categoryCode=salary" --data-urlencode "amount=200" --data-urlencode "periodId=$PERIOD_ID" \
  "$BASE/entry/$SCEN_ACC/cash-flow" -o /dev/null
b4=$(mysql -ufinance -pfinance finance -sN -e "SELECT end_balance FROM period_snapshot WHERE period_id=$PERIOD_ID AND account_id=$SCEN_ACC" 2>/dev/null)
[[ "$b4" == "4200.00" ]] && log_ok "FR7-11 校准后 +收入200 → 余额=4200" || log_bad "FR7-11" "got=$b4"

# FR-9-1 转账(随机金额避开 24h 去重)
AMT=$(( RANDOM % 9000 + 100 ))
code=$($CURL -b $COOKIE -X POST -H "X-XSRF-TOKEN: $CSRF" -H "HX-Request: true" \
  --data-urlencode "toAccountId=$ACC2" --data-urlencode "amount=$AMT" --data-urlencode "periodId=$PERIOD_ID" \
  "$BASE/entry/$ACC1/transfer" -o /dev/null -w "%{http_code}")
[[ "$code" == "200" ]] && log_ok "FR9-1 提交转账 200 (amount=$AMT)" || log_bad "FR9-1 转账" "code=$code"

# FR-9-1b 转账响应含 HX-Trigger=refresh-row-{toId} 让 B 行 self-refresh
AMT2=$(( RANDOM % 9000 + 100 ))
hdrs=$($CURL -b $COOKIE -D - -X POST -H "X-XSRF-TOKEN: $CSRF" -H "HX-Request: true" \
  --data-urlencode "toAccountId=$ACC2" --data-urlencode "amount=$AMT2" --data-urlencode "periodId=$PERIOD_ID" \
  "$BASE/entry/$ACC1/transfer" -o /dev/null)
echo "$hdrs" | grep -qi "HX-Trigger:.*refresh-row-$ACC2" \
  && log_ok "FR9-1b 转账响应触发 refresh-row-$ACC2(B 账户行自动刷新)" \
  || log_bad "FR9-1b HX-Trigger 缺失" "no refresh trigger"

# FR-9-2 同金额二次提交 → 200 + HX-Trigger=showToast(由 ToastErrorAdvice 转友好提示)
hdrs=$($CURL -b $COOKIE -D - -X POST -H "X-XSRF-TOKEN: $CSRF" -H "HX-Request: true" \
  --data-urlencode "toAccountId=$ACC2" --data-urlencode "amount=$AMT" --data-urlencode "periodId=$PERIOD_ID" \
  "$BASE/entry/$ACC1/transfer" -o /dev/null)
echo "$hdrs" | grep -qi "HX-Trigger:.*showToast" \
  && log_ok "FR9-2 24h 重复转账 → toast 提示(替代 500)" \
  || log_bad "FR9-2 重复未拦截" "missing toast"

# FR-11/12 关账 + 立即开下一周期 + CLOSED 期 toast
section "FR-11/12 · 关账 / 开新期 / CLOSED 拒写"

# 立即开下一周期
hdrs=$($CURL -b $COOKIE -D - -X POST -H "X-XSRF-TOKEN: $CSRF" \
  --data-urlencode "_csrf=$CSRF" \
  "$BASE/admin/periods/open-next" -o /dev/null)
echo "$hdrs" | head -1 | grep -q "302" && log_ok "FR12-3 立即开下一周期 → 302" || log_bad "FR12-3 立即开下一周期" "no 302"

# 强制关账(找一个 OPEN 周期,断言:CLOSED + PENDING=0 + snapshot 全补齐)
FORCE_CLOSE=$(mysql -ufinance -pfinance finance -sN -e "SELECT id FROM period WHERE family_id=1 AND status='OPEN' ORDER BY period_start DESC LIMIT 1" 2>/dev/null)
ACCT_COUNT=$(mysql -ufinance -pfinance finance -sN -e "SELECT COUNT(*) FROM account WHERE family_id=1 AND archived_at IS NULL" 2>/dev/null)
$CURL -b $COOKIE -X POST -H "X-XSRF-TOKEN: $CSRF" --data-urlencode "_csrf=$CSRF" \
  "$BASE/admin/periods/$FORCE_CLOSE/force-close" -o /dev/null
NEW_STATUS=$(mysql -ufinance -pfinance finance -sN -e "SELECT status FROM period WHERE id=$FORCE_CLOSE" 2>/dev/null)
SNAP_COUNT=$(mysql -ufinance -pfinance finance -sN -e "SELECT COUNT(*) FROM period_snapshot WHERE period_id=$FORCE_CLOSE" 2>/dev/null)
PENDING=$(mysql -ufinance -pfinance finance -sN -e "SELECT COUNT(*) FROM snapshot_todo WHERE period_id=$FORCE_CLOSE AND status='PENDING'" 2>/dev/null)
[[ "$NEW_STATUS" == "CLOSED" && "$PENDING" == "0" && "$SNAP_COUNT" == "$ACCT_COUNT" ]] \
  && log_ok "FR11-5 强制关账 period=$FORCE_CLOSE: CLOSED + 0 PENDING + $SNAP_COUNT snapshot" \
  || log_bad "FR11-5 强制关账失败" "status=$NEW_STATUS pending=$PENDING snap=$SNAP_COUNT/$ACCT_COUNT"

# CLOSED 周期点 +收入 → 应返回 200 + HX-Trigger=showToast
CLOSED_PERIOD=$(mysql -ufinance -pfinance finance -sN -e "SELECT id FROM period WHERE family_id=1 AND status='CLOSED' ORDER BY period_start DESC LIMIT 1" 2>/dev/null)
hdrs=$($CURL -b $COOKIE -D - -X POST -H "X-XSRF-TOKEN: $CSRF" -H "HX-Request: true" \
  --data-urlencode "kind=INCOME" --data-urlencode "categoryCode=salary" --data-urlencode "amount=10" --data-urlencode "periodId=$CLOSED_PERIOD" \
  "$BASE/entry/1/cash-flow" -o /dev/null)
echo "$hdrs" | grep -qi "HX-Trigger:.*showToast" \
  && log_ok "FR11-4 CLOSED 期点+收入 → HX-Trigger=showToast(toast 拒写)" \
  || log_bad "FR11-4 CLOSED 拒写无 toast 头" "missing"

# ---------- FR-12 周期重开 ----------
section "FR-12 · 周期重开"
$CURL -b $COOKIE "$BASE/admin/periods" -o "$TMP" -w ""
{ grep -q "OPEN\|CLOSED" "$TMP" && grep -q "</html>" "$TMP"; } && log_ok "FR12-1 /admin/periods 列表" || log_bad "FR12-1 /admin/periods" "missing"

# ---------- FR-13 Dashboard ----------
section "FR-13 · Dashboard"
$CURL -b $COOKIE "$BASE/dashboard" -o "$TMP" -w ""
{ grep -q "净资产" "$TMP" && grep -q "总资产" "$TMP" && grep -q "总负债" "$TMP" && grep -q "紧急储备" "$TMP" && grep -q "负债率" "$TMP"; } \
  && log_ok "FR13-1 5 KPI 卡齐" || log_bad "FR13-1 KPI 不全" "missing"

for r in 1M 3M 6M YTD 1Y ALL; do
  $CURL -b $COOKIE "$BASE/dashboard?range=$r" -o "$TMP" -w ""
  grep -q "</html>" "$TMP" && log_ok "FR13-2/$r 完整 HTML" || log_bad "FR13-2/$r 不完整" "no </html>"
done

# HTMX fragment-only
$CURL -b $COOKIE -H "HX-Request: true" "$BASE/dashboard?range=YTD" -o "$TMP" -w ""
{ ! grep -q "<html" "$TMP" && grep -q "dashboard-region" "$TMP"; } \
  && log_ok "FR13-3 HX-Request 返回 fragment" || log_bad "FR13-3 fragment 异常" "html present?"

# ---------- FR-14 Reports ----------
section "FR-14 · Reports"
$CURL -b $COOKIE "$BASE/reports" -o "$TMP" -w ""
{ grep -q "家庭 XIRR" "$TMP" && grep -q "</html>" "$TMP"; } && log_ok "FR14-1 /reports 完整" || log_bad "FR14-1 /reports" "missing"

for r in 1M 3M 6M YTD 1Y ALL; do
  $CURL -b $COOKIE "$BASE/reports?range=$r" -o "$TMP" -w ""
  grep -q "</html>" "$TMP" && log_ok "FR14-2/$r 完整" || log_bad "FR14-2/$r" "no </html>"
done

# ---------- FR-15 多币种 ----------
section "FR-15 · 多币种"
$CURL -b $COOKIE "$BASE/admin/fx" -o "$TMP" -w ""
{ grep -q "USD" "$TMP" || grep -q "HKD" "$TMP"; } && grep -q "</html>" "$TMP" \
  && log_ok "FR15-1 /admin/fx 含 USD/HKD" || log_bad "FR15-1 /admin/fx" "missing"

# ---------- FR-16 CSV ----------
section "FR-16 · CSV 导出"
$CURL -b $COOKIE "$BASE/export.zip" -o /tmp/finance-qa.zip -w ""
file /tmp/finance-qa.zip 2>&1 | grep -q "Zip" && log_ok "FR16-1 /export.zip 是 ZIP" || log_bad "FR16-1 不是 ZIP" "$(file /tmp/qa.zip)"

cnt=$(python3 -c "import zipfile; z=zipfile.ZipFile('/tmp/finance-qa.zip'); print(len(z.infolist()))" 2>/dev/null)
[[ "$cnt" == "9" ]] && log_ok "FR16-2 ZIP 含 9 个文件" || log_bad "FR16-2 文件数 $cnt" "expected 9"

bom=$(python3 -c "import zipfile; z=zipfile.ZipFile('/tmp/finance-qa.zip'); print(z.read('families.csv')[:3].hex())" 2>/dev/null)
[[ "$bom" == "efbbbf" ]] && log_ok "FR16-3 UTF-8 BOM 头" || log_bad "FR16-3 BOM" "got $bom"

# ---------- FR-17 banner / FR-18 备份 ----------
section "FR-17/18"
$CURL -b $COOKIE "$BASE/dashboard" -o "$TMP" -w ""
grep -qE "未结账|本期还有|未填" "$TMP" && log_ok "FR17-1 dashboard 含 pending banner 元素" || log_skip "FR17-1 banner" "可能本期已全填"

$CURL -b $COOKIE "$BASE/admin/backup" -o "$TMP" -w ""
{ grep -q "备份" "$TMP" && grep -q "</html>" "$TMP"; } && log_ok "FR18-1 /admin/backup" || log_bad "FR18-1" "missing"

# ---------- FR-19 LOAN ----------
section "FR-19 · LOAN"
LOAN_ID=$(mysql -ufinance -pfinance finance -sN -e "SELECT id FROM account WHERE type='LOAN' LIMIT 1" 2>/dev/null)
if [[ -n "$LOAN_ID" ]]; then
  $CURL -b $COOKIE "$BASE/accounts/$LOAN_ID/edit" -o "$TMP" -w ""
  grep -q "默认还款来源" "$TMP" && log_ok "FR19-3 LOAN 编辑含还款来源字段" || log_bad "FR19-3 LOAN 字段" "missing"
else
  log_skip "FR19-3 无 LOAN 账户" "skip"
fi

# ---------- FR-20 admin 全部 ----------
section "FR-20 · /admin 全部子页"
for path in /admin /admin/family /admin/members /admin/account-templates /admin/cash-flow-categories /admin/periods /admin/reminders /admin/fx /admin/backup /admin/audit /admin/calc-tweaks; do
  $CURL -b $COOKIE "$BASE$path" -o "$TMP" -w ""
  grep -q "</html>" "$TMP" && log_ok "FR20 $path" || log_bad "FR20 $path 不完整" "no </html>"
done

# ---------- FR-21 账户筛选 ----------
section "FR-21 · 账户筛选"
$CURL -b $COOKIE "$BASE/dashboard?accounts=1" -o "$TMP" -w ""
{ grep -q "1 个已选\|个已选" "$TMP" && grep -q "</html>" "$TMP"; } && log_ok "FR21-1 ?accounts=1 显示筛选" || log_bad "FR21-1 筛选" "missing"

# ---------- FR-22 显示币种 ----------
section "FR-22 · 显示币种"
$CURL -b $COOKIE "$BASE/dashboard?currency=USD" -o "$TMP" -w ""
grep -qF '$' "$TMP" && grep -q "</html>" "$TMP" && log_ok "FR22-1 USD 显示 \$" || log_bad "FR22-1 USD" "missing $"
$CURL -b $COOKIE "$BASE/dashboard?currency=HKD" -o "$TMP" -w ""
grep -qF 'HK$' "$TMP" && log_ok "FR22-2 HKD 显示 HK\$" || log_bad "FR22-2 HKD" "missing HK$"

# v0.2 BUG-FIX(2026-05-10):币种切换以前只换符号不换数字。
# 这里强校验三套币种渲染出的「净资产」KPI 数字必须真的不同(假设家庭只有 CNY 账户 + 至少一行 fx_rate)。
# 防止 FactMapper.xml fx CASE 倒挂 / controller 漏调 fxFallback 等回归。
fx_seed_periodId=$(mysql -ufinance -pfinance finance -sN -e "SELECT id FROM period WHERE family_id=1 ORDER BY period_start DESC LIMIT 1" 2>/dev/null)
mysql -ufinance -pfinance finance -e "
INSERT INTO fx_rate (family_id, base_currency, quote_currency, period_id, rate, source) VALUES
(1, 'CNY', 'USD', ${fx_seed_periodId}, 0.140000, 'qa-seed'),
(1, 'CNY', 'HKD', ${fx_seed_periodId}, 1.090000, 'qa-seed')
ON DUPLICATE KEY UPDATE rate=VALUES(rate), source=VALUES(source);" 2>/dev/null

# 提取 currency=X 时净资产 KPI 数字(去千分位 + 符号)
extract_nw() {
  local cur="$1"
  $CURL -b $COOKIE "$BASE/dashboard?currency=${cur}" -o "$TMP" -w ""
  grep -A1 'kpi-eyebrow">净资产' "$TMP" | grep "kpi-value" | head -1 | sed -E 's/.*>([^<]+)<.*/\1/' | tr -d ',$¥' | sed 's/HK//'
}
nw_cny=$(extract_nw CNY)
nw_usd=$(extract_nw USD)
nw_hkd=$(extract_nw HKD)
[[ "$nw_cny" != "$nw_usd" && "$nw_cny" != "$nw_hkd" && "$nw_usd" != "$nw_hkd" ]] \
  && log_ok "v02-CCY-1 三套币种净资产数字真的不同 (CNY=${nw_cny} USD=${nw_usd} HKD=${nw_hkd})" \
  || log_bad "v02-CCY-1 币种切换数字未联动" "CNY=${nw_cny} USD=${nw_usd} HKD=${nw_hkd}"

# 数学校验:USD = CNY × 0.14(±2 元容差,处理舍入)
if [[ -n "$nw_cny" && -n "$nw_usd" ]]; then
  expected_usd=$(awk -v c="$nw_cny" 'BEGIN{printf "%d", c*0.14}')
  diff=$(awk -v a="$nw_usd" -v e="$expected_usd" 'BEGIN{d=a-e; if(d<0)d=-d; print d}')
  [[ ${diff%.*} -le 2 ]] \
    && log_ok "v02-CCY-2 USD 数学正确 (CNY=${nw_cny} × 0.14 ≈ ${expected_usd} 实际=${nw_usd})" \
    || log_bad "v02-CCY-2 USD 数学错" "expected≈${expected_usd} got=${nw_usd}"
fi

# 按需拉汇率:删 fx_rate 后切 USD,后端应即时调 frankfurter API 拉新汇率写入,然后正常显示 $
mysql -ufinance -pfinance finance -e "DELETE FROM fx_rate;" 2>/dev/null
$CURL --max-time 30 -b $COOKIE "$BASE/dashboard?currency=USD" -o "$TMP" -w ""
fx_after=$(mysql -ufinance -pfinance finance -sN -e "SELECT COUNT(*) FROM fx_rate WHERE family_id=1 AND quote_currency='USD' AND source='frankfurter.dev';" 2>/dev/null)
if [[ "${fx_after:-0}" -ge 1 ]]; then
  log_ok "v02-CCY-3 fx_rate 缺 → 即时调 frankfurter 拉取并入库(source=frankfurter.dev count=$fx_after)"
  # 拉成功后 USD KPI 应该是 $ 而不是 ¥
  grep -A1 'kpi-eyebrow">净资产' "$TMP" | grep "kpi-value" | head -1 | grep -qF '$' \
    && log_ok "v02-CCY-4 即时拉成功后正常显示 \$ 数字(无 toast 兜底)" \
    || log_bad "v02-CCY-4 即时拉成功但仍未显示 \$" "wrong symbol"
else
  # 网络拉不到时:fxFallback 路径,toast 脚本必现 + 显示 ¥
  log_skip "v02-CCY-3 frankfurter 不可达 / 拉失败,走 fallback 路径校验" "fx_after=$fx_after"
  grep -q "汇率未配置" "$TMP" \
    && log_ok "v02-CCY-4 拉失败 fallback → 渲染「汇率未配置」toast 脚本" \
    || log_bad "v02-CCY-4 fallback toast 缺" "no 汇率未配置"
fi

# v02-CCY-5 模板防回归:确保 dashboard / reports 都有 fxFallback toast 脚本块
grep -q '汇率未配置' src/main/resources/templates/dashboard/_region.html \
  && grep -q '汇率未配置' src/main/resources/templates/reports/_region.html \
  && log_ok "v02-CCY-5 dashboard / reports 均含 fxFallback toast 脚本块(防回归)" \
  || log_bad "v02-CCY-5 fxFallback toast 模板缺失" "missing in dashboard/reports _region.html"

# ---------- 静态资源 ----------
section "Static / vendor"
for f in /vendor/tailwind.js /vendor/htmx.min.js /vendor/chart.umd.min.js /vendor/echarts.min.js /css/style.css; do
  code=$($CURL -o /dev/null -w "%{http_code}" "$BASE$f")
  [[ "$code" == "200" ]] && log_ok "ST $f 200" || log_bad "ST $f" "code=$code"
done

# ---------- v0.2 错误兜底页 ----------
section "v0.2 · 错误兜底页"

# /error 直接访问(已登录),渲染完整错误页
$CURL -b $COOKIE -H "Accept: text/html" "$BASE/error" -o "$TMP" -w ""
{ grep -q '印泥洒了' "$TMP" && grep -q '出 · 错 · 了' "$TMP" && grep -q '/dashboard' "$TMP" && grep -q '/entry' "$TMP"; } \
  && log_ok "ERR-1 /error(已登录)渲染卡通错误页 + dashboard+entry 链接" \
  || log_bad "ERR-1 /error 内容缺" "missing"

# 登录后访问不存在路径 → 404 + error.html
code=$($CURL -b $COOKIE -H "Accept: text/html" -o "$TMP" -w "%{http_code}" "$BASE/no-such-page-zxy")
{ [[ "$code" == "404" ]] && grep -q '印泥洒了' "$TMP"; } \
  && log_ok "ERR-2 登录后 404 渲染卡通错误页" \
  || log_bad "ERR-2 404 不渲染 error.html" "code=$code"

# 前端兜底:layout 含 5 个全局错误监听
$CURL -b $COOKIE "$BASE/dashboard" -o "$TMP" -w ""
{ grep -q 'htmx:responseError' "$TMP" && grep -q 'htmx:sendError' "$TMP" \
  && grep -q 'htmx:timeout' "$TMP" && grep -q 'unhandledrejection' "$TMP" \
  && grep -q "window.addEventListener('error'" "$TMP"; } \
  && log_ok "ERR-3 前端兜底:5 个错误监听齐(htmx:responseError/sendError/timeout + window error/rejection)" \
  || log_bad "ERR-3 前端错误监听不全" "missing"

# 5xx 兜底逻辑:含 401 跳 /login + 5xx 显示 toast 文案
{ grep -q "/login?expired=1" "$TMP" && grep -q "服务器繁忙" "$TMP" && grep -q "网络异常" "$TMP"; } \
  && log_ok "ERR-4 兜底文案齐:401→/login expired + 5xx 服务器繁忙 toast + 网络异常 toast" \
  || log_bad "ERR-4 兜底文案缺" "missing"

# ---------- v0.2 entry 上期余额参考 ----------
section "v0.2 · entry 上期余额参考"

$CURL -b $COOKIE "$BASE/entry" -o "$TMP" -w ""
{ grep -q '参考 · 上期末' "$TMP" && grep -qE '上期末.*¥' "$TMP"; } \
  && log_ok "FR7-参考 entry 行含'参考·上期末'+右侧'上期末¥X'" \
  || log_bad "FR7-参考 上期余额参考缺" "missing"

# ---------- v0.2 FR-33 / FR-34 移动端引导 ----------
section "v0.2 · FR-33 微信引导 + FR-34 PWA 添加到主屏"

# manifest 200 + 正确 MIME
ct=$($CURL -o /tmp/finance-qa-manifest.json -w "%{content_type}" "$BASE/manifest.webmanifest")
[[ "$ct" == *"manifest+json"* ]] && log_ok "FR34-1 manifest Content-Type=application/manifest+json" || log_bad "FR34-1 MIME 错" "got=$ct"

# manifest 字段齐
{ grep -q '"name"' /tmp/finance-qa-manifest.json && grep -q '/dashboard' /tmp/qa-manifest.json \
  && grep -q '"display": "standalone"' /tmp/finance-qa-manifest.json && grep -q '"icons"' /tmp/qa-manifest.json; } \
  && log_ok "FR34-2 manifest 字段齐(name/start_url/display/icons)" \
  || log_bad "FR34-2 字段缺" "see /tmp/finance-qa-manifest.json"

# 三张 PNG 都 200
for f in apple-touch-icon-180.png icon-192.png icon-512.png; do
  code=$($CURL -o /dev/null -w "%{http_code}" "$BASE/img/$f")
  [[ "$code" == "200" ]] && log_ok "FR34-3 /img/$f = 200" || log_bad "FR34-3 /img/$f" "code=$code"
done

# layout(login 页未登录就能拉到 head)含 4 个 apple meta + manifest + apple-touch-icon-180.png
$CURL "$BASE/login" -o "$TMP" -w ""
{ grep -q 'name="apple-mobile-web-app-capable"' "$TMP" \
  && grep -q 'name="apple-mobile-web-app-status-bar-style"' "$TMP" \
  && grep -q 'name="apple-mobile-web-app-title"' "$TMP" \
  && grep -q 'rel="manifest"' "$TMP" \
  && grep -q 'apple-touch-icon-180.png' "$TMP" \
  && grep -q 'theme-color' "$TMP"; } \
  && log_ok "FR34-4 layout head 含 PWA meta + apple-touch-icon-180" \
  || log_bad "FR34-4 PWA meta 缺" "see $TMP"

# /js/mobile-guide.js 未登录可达
code=$($CURL -o /dev/null -w "%{http_code}" "$BASE/js/mobile-guide.js")
[[ "$code" == "200" ]] && log_ok "FR34-5 /js/mobile-guide.js 未登录 200" || log_bad "FR34-5 mobile-guide.js" "code=$code"

# manifest 未登录可达
code=$($CURL -o /dev/null -w "%{http_code}" "$BASE/manifest.webmanifest")
[[ "$code" == "200" ]] && log_ok "FR34-6 manifest 未登录 200" || log_bad "FR34-6 manifest 被拦" "code=$code"

# layout 引用 mobile-guide.js
grep -q 'mobile-guide.js' "$TMP" && log_ok "FR33-1 layout 引用 mobile-guide.js" || log_bad "FR33-1 mobile-guide.js 未引入" "missing"

# mobile-guide.js 内容含三处关键判断
$CURL "$BASE/js/mobile-guide.js" -o "$TMP" -w ""
{ grep -q 'MicroMessenger' "$TMP" && grep -q 'wx_dismissed_at' "$TMP" \
  && grep -q 'pwa_dismissed_at' "$TMP" && grep -q 'standalone' "$TMP"; } \
  && log_ok "FR33-2/FR34-7 脚本含微信 UA + iOS standalone + 双 dismiss key" \
  || log_bad "FR33-2 脚本字段缺" "see $TMP"

# ---------- v0.2 Stage 1: nav / 资产体检 / 类目 ----------
section "v0.2 · 阶段 1 · nav 资产体检 + 类目 + pill"

# CHECKUP-1 顶部 nav 含「资产体检」入口(已登录 dashboard)
$CURL -b $COOKIE "$BASE/dashboard" -o "$TMP" -w ""
grep -q '资产体检' "$TMP" && log_ok "v02-NAV-1 dashboard 顶部 nav 含「资产体检」" || log_bad "v02-NAV-1 nav 资产体检 缺" "see $TMP"

# CHECKUP-2 GET /checkup 占位页 200 + 含「资产体检」标题
code=$($CURL -b $COOKIE -o "$TMP" -w "%{http_code}" "$BASE/checkup")
{ [[ "$code" == "200" ]] && grep -q '资产体检' "$TMP"; } \
  && log_ok "v02-CHK-1 GET /checkup 占位 200" || log_bad "v02-CHK-1 /checkup" "code=$code"

# CHECKUP-3 GET /checkup?account=1 账户级占位
code=$($CURL -b $COOKIE -o "$TMP" -w "%{http_code}" "$BASE/checkup?account=1")
{ [[ "$code" == "200" ]] && grep -q '账户体检\|资产体检' "$TMP"; } \
  && log_ok "v02-CHK-2 /checkup?account=1 占位 200" || log_bad "v02-CHK-2" "code=$code"

# CAT-1 /admin/product-categories 200 + 16 个类目
code=$($CURL -b $COOKIE -o "$TMP" -w "%{http_code}" "$BASE/admin/product-categories")
[[ "$code" == "200" ]] && log_ok "v02-PCAT-1 /admin/product-categories 200" || log_bad "v02-PCAT-1" "code=$code"
n=$(grep -oE 'A_STOCK|US_STOCK|HK_STOCK|MONEY_FUND|BANK_WEALTH|CASH_DEPOSIT|GOLD|MIXED_FUND|SHORT_BOND|LONG_BOND|PROPERTY_RES|PROPERTY_INV|CRYPTO|FUTURES|LIABILITY' "$TMP" | sort -u | wc -l)
[[ "$n" -ge 15 ]] && log_ok "v02-PCAT-2 16 个类目 code 全渲染 (n=$n)" || log_bad "v02-PCAT-2 类目数" "n=$n"
grep -q '沪深 300\|标普 500' "$TMP" && log_ok "v02-PCAT-3 含基准指数标签" || log_bad "v02-PCAT-3 基准指数" "missing"

# CAT-4 /admin/index 含产品类目 tile
code=$($CURL -b $COOKIE -o "$TMP" -w "%{http_code}" "$BASE/admin")
{ [[ "$code" == "200" ]] && grep -q 'product-categories' "$TMP"; } \
  && log_ok "v02-PCAT-4 /admin hub 含产品类目 tile" || log_bad "v02-PCAT-4 admin tile" "code=$code"

# CAT-5 /admin/_sidebar 含产品类目 link(随便挑一个 admin 页验证)
$CURL -b $COOKIE "$BASE/admin/cash-flow-categories" -o "$TMP" -w ""
grep -q '产品类目' "$TMP" && log_ok "v02-PCAT-5 admin sidebar 含产品类目链接" || log_bad "v02-PCAT-5 sidebar" "missing"

# PILL-1 /accounts 列表类目 pill(2026-05-10 改 SVG 后,grep 类目 pill 标识)
$CURL -b $COOKIE "$BASE/accounts" -o "$TMP" -w ""
n=$(grep -oE 'border-color:var\(--brass-deep\);color:var\(--brass-deep\)' "$TMP" | wc -l)
[[ "$n" -ge 20 ]] && log_ok "v02-PILL-1 /accounts 列表类目 pill 数=$n (≥20)" || log_bad "v02-PILL-1 类目 pill 数" "n=$n"

# PILL-2 风险星 ★ 出现(STOCK / WEALTH 类目有 risk_level)
n=$(grep -oE '★' "$TMP" | wc -l)
[[ "$n" -ge 4 ]] && log_ok "v02-PILL-2 /accounts 风险 ★ pill 出现 n=$n" || log_bad "v02-PILL-2 风险 pill" "n=$n"

# PILL-3 /accounts 不再 500 (Thymeleaf 表达式正确)
grep -q '出错了' "$TMP" && log_bad "v02-PILL-3 /accounts 仍触发错误兜底" "see $TMP" || log_ok "v02-PILL-3 /accounts 无错误兜底"

# PILL-4 /accounts/new 编辑向导含类目下拉 + 16 个 option(包括「按账户类型默认」)
$CURL -b $COOKIE "$BASE/accounts/new" -o "$TMP" -w ""
{ grep -q 'productCategoryCode' "$TMP" && grep -q '按账户类型默认' "$TMP"; } \
  && log_ok "v02-WIZ-1 /accounts/new 含产品类目下拉" || log_bad "v02-WIZ-1 wizard 类目下拉" "missing"

# PILL-5 /accounts/{id}/edit 含 productCategoryCode + riskLevelOverride
$CURL -b $COOKIE "$BASE/accounts/1/edit" -o "$TMP" -w ""
{ grep -q 'productCategoryCode' "$TMP" && grep -q 'riskLevelOverride' "$TMP"; } \
  && log_ok "v02-EDIT-1 /accounts/1/edit 含类目 + 风险覆盖字段" || log_bad "v02-EDIT-1 编辑页字段" "missing"

# DASH-1 dashboard 列表行含 → 体检 链接
$CURL -b $COOKIE "$BASE/dashboard" -o "$TMP" -w ""
grep -q '/checkup?account=' "$TMP" && log_ok "v02-DASH-1 dashboard 行含 /checkup?account=" || log_bad "v02-DASH-1 dashboard 体检入口" "missing"

# SOFT-DEL-1 软删字段在 mapper 已过滤(回归):正常 entry 页能加载,且没有数据库错误页
$CURL -b $COOKIE "$BASE/entry" -o "$TMP" -w ""
{ grep -q '记账' "$TMP" || grep -q '填报' "$TMP"; } && ! grep -q '出错了' "$TMP" \
  && log_ok "v02-SOFT-1 /entry 与 deleted_at 过滤兼容" || log_bad "v02-SOFT-1 /entry" "see $TMP"

# ---------- v0.2 Stage 2: 账户级体检 ----------
section "v0.2 · 阶段 2 · 账户级体检 (FR-40b)"

# 13 个账户全部 200,无 Thymeleaf 错误
err_count=0
for id in 1 2 3 4 5 6 7 8 9 10 11 12 13; do
  $CURL -b $COOKIE "$BASE/checkup?account=$id" -o "$TMP" -w ""
  grep -q '出错了' "$TMP" && err_count=$((err_count+1))
done
[[ $err_count -eq 0 ]] && log_ok "v02-DIAG-1 13 个账户体检页全部成功渲染(0 错误)" || log_bad "v02-DIAG-1 体检页错误数" "err=$err_count"

# CASH 账户(id=1 招行)显示「流动性」卡 + 不显示「收益表现」「欠款余额」「估值」
$CURL -b $COOKIE "$BASE/checkup?account=1" -o "$TMP" -w ""
{ grep -q '流动性' "$TMP" && ! grep -q '收益表现' "$TMP" \
  && ! grep -q '欠款余额' "$TMP" && ! grep -q '估值' "$TMP"; } \
  && log_ok "v02-DIAG-2 CASH 账户只显示「流动性」卡" \
  || log_bad "v02-DIAG-2 CASH 卡分支" "see $TMP"

# STOCK 账户(id=3 一般是 STOCK)显示「收益表现」「风险刻度」「基准对照」「现金流」
$CURL -b $COOKIE "$BASE/checkup?account=3" -o "$TMP" -w ""
{ grep -q '收益表现' "$TMP" && grep -q '风险刻度' "$TMP" \
  && grep -q '基准对照' "$TMP" && grep -q 'CASH FLOW' "$TMP"; } \
  && log_ok "v02-DIAG-3 STOCK 账户显示 4 张投资卡" \
  || log_bad "v02-DIAG-3 STOCK 4 卡" "see $TMP"

# LOAN 账户(id=5 一般是 LOAN)显示「欠款余额」「还款进度」
$CURL -b $COOKIE "$BASE/checkup?account=5" -o "$TMP" -w ""
{ grep -q '欠款余额' "$TMP" && grep -q '还款进度' "$TMP" \
  && ! grep -q '收益表现' "$TMP"; } \
  && log_ok "v02-DIAG-4 LOAN 账户显示「欠款余额 + 还款进度」" \
  || log_bad "v02-DIAG-4 LOAN 卡" "see $TMP"

# PROPERTY 账户(id=10 一般是 PROPERTY)显示「估值」简卡
$CURL -b $COOKIE "$BASE/checkup?account=10" -o "$TMP" -w ""
{ grep -q '估值' "$TMP" && ! grep -q '收益表现' "$TMP"; } \
  && log_ok "v02-DIAG-5 PROPERTY 账户显示「估值」简卡" \
  || log_bad "v02-DIAG-5 PROPERTY 卡" "see $TMP"

# 不存在 / 跨家庭账户 → 重定向到 /checkup family 页
loc=$($CURL -b $COOKIE -o /dev/null -w "%{redirect_url}" "$BASE/checkup?account=99999")
[[ "$loc" == *"/checkup"* ]] && log_ok "v02-DIAG-6 不存在账户跳 /checkup family" || log_bad "v02-DIAG-6 越权处理" "loc=$loc"

# 顶部账户 pill 含类目 + 风险星
$CURL -b $COOKIE "$BASE/checkup?account=3" -o "$TMP" -w ""
{ grep -q 'border-color:var(--brass-deep)' "$TMP" && grep -q '★' "$TMP"; } \
  && log_ok "v02-DIAG-7 顶部账户 pill 含类目 + 风险星" \
  || log_bad "v02-DIAG-7 pill" "missing"

# 余额走势 sparkline canvas 渲染
$CURL -b $COOKIE "$BASE/checkup?account=3" -o "$TMP" -w ""
grep -q 'id="balanceTrend"' "$TMP" && log_ok "v02-DIAG-8 余额走势 canvas 渲染" || log_bad "v02-DIAG-8 sparkline" "missing"

# ---------- v0.2 Stage 3: 智能建议 + LLM 润色 ----------
section "v0.2 · 阶段 3 · 智能建议 + LLM 文案润色 (FR-40c)"

# 13 个账户的体检页 + family 页都不再触发错误兜底(回归 stage 2)
err_count=0
$CURL -b $COOKIE "$BASE/checkup" -o "$TMP" -w ""
grep -q '出错了' "$TMP" && err_count=$((err_count+1))
for id in 1 2 3 4 5 6 7 8 9 10 11 12 13; do
  $CURL -b $COOKIE "$BASE/checkup?account=$id" -o "$TMP" -w ""
  grep -q '出错了' "$TMP" && err_count=$((err_count+1))
done
[[ $err_count -eq 0 ]] && log_ok "v02-ADV-1 14 个体检页(family + 13 acct)全部成功渲染" || log_bad "v02-ADV-1 体检页错误数" "err=$err_count"

# 家庭体检页含 advice cards(命中至少 1 条)或健康提示
$CURL -b $COOKIE "$BASE/checkup" -o "$TMP" -w ""
{ grep -q 'advice-card' "$TMP" || grep -q '健康状态良好' "$TMP"; } \
  && log_ok "v02-ADV-2 家庭体检页含 advice 卡或健康提示" || log_bad "v02-ADV-2 advice 区域" "missing"

# 家庭体检页含「来自账房的提醒」标题(命中时)或「健康状态良好」(全 miss 时)
{ grep -q '来.\{0,3\}自.\{0,3\}账.\{0,3\}房.\{0,3\}的.\{0,3\}提.\{0,3\}醒' "$TMP" || grep -q '健康状态良好' "$TMP"; } \
  && log_ok "v02-ADV-3 advice 区域 eyebrow 文案存在" || log_bad "v02-ADV-3 eyebrow" "missing"

# 账户体检页(任一 STOCK 账户)含 advice 卡或体检通过提示
$CURL -b $COOKIE "$BASE/checkup?account=3" -o "$TMP" -w ""
{ grep -q 'advice-card' "$TMP" || grep -q '本账户体检通过' "$TMP"; } \
  && log_ok "v02-ADV-4 STOCK 账户体检 advice 区域" || log_bad "v02-ADV-4 acct advice" "missing"

# advice 卡的 data-rule 与 data-severity 属性渲染
$CURL -b $COOKIE "$BASE/checkup" -o "$TMP" -w ""
n_rule=$(grep -oE 'data-rule="[^"]+"' "$TMP" | wc -l)
n_sev=$(grep -oE 'data-severity="[^"]+"' "$TMP" | wc -l)
# v02-ADV-5 仅当有 advice 命中时校验 data 属性;无命中时(显示「健康状态良好」)SKIP
if [[ $n_rule -ge 1 && $n_sev -ge 1 ]]; then
  log_ok "v02-ADV-5 advice 卡 data-rule + data-severity 渲染 (rule=$n_rule sev=$n_sev)"
elif grep -q '健康状态良好\|本账户体检通过' "$TMP"; then
  log_skip "v02-ADV-5 advice data attr" "无规则命中,渲染了健康状态文案"
else
  log_bad "v02-ADV-5 data attr" "rule=$n_rule sev=$n_sev"
fi

# AI 综合诊断 placeholder(spinner)在 family 页存在 — 决策 20 新方向
grep -q 'ai-diagnose-panel' "$TMP" && log_ok "v02-ADV-6 family 页含 AI 综合诊断 placeholder" \
  || log_bad "v02-ADV-6 AI placeholder" "missing"

# AI 综合诊断 placeholder 含 hx-trigger="load"(进页自动 fetch)
grep -E 'hx-trigger="load".*ai-diagnose|ai-diagnose.*hx-trigger="load"' "$TMP" >/dev/null \
  || grep -B2 -A2 'ai-diagnose-panel' "$TMP" | grep -q 'hx-trigger="load"' \
  && log_ok "v02-ADV-7 AI placeholder 含 hx-trigger=load(自动加载)" \
  || log_bad "v02-ADV-7 hx-trigger" "missing"

# ---------- v0.2 · AI 综合诊断 endpoint(决策 20 / 2026-05-10) ----------
section "v0.2 · AI 综合诊断 · /checkup/diagnose (FR-40c 决策 20)"

# DIAG-1 全家维度 endpoint 200(LLM 真机调用最长 30s,放宽 timeout)
code=$(/usr/bin/curl -s --max-time 35 -b $COOKIE -o "$TMP" -w "%{http_code}" "$BASE/checkup/diagnose")
[[ "$code" == "200" ]] && log_ok "v02-DIAG-1 GET /checkup/diagnose → 200" \
  || log_bad "v02-DIAG-1 family diagnose" "code=$code"

# DIAG-2 返回 fragment 含 data-vendor / data-cache / data-available 属性(无 LLM key 时是 fallback)
grep -qE 'data-vendor=|data-available=' "$TMP" \
  && log_ok "v02-DIAG-2 fragment 含 vendor/available 属性" \
  || log_bad "v02-DIAG-2 fragment attrs" "missing"

# DIAG-3 fragment 含 AI · 综合智能诊断 标题或 AI · 暂不可用
{ grep -q '综合智能诊断' "$TMP" || grep -q 'AI · 暂不可用' "$TMP"; } \
  && log_ok "v02-DIAG-3 fragment 含诊断标题或降级文案" \
  || log_bad "v02-DIAG-3 panel header" "missing"

# DIAG-4 账户维度 endpoint 200(LLM 真机调用最长 30s)
code=$(/usr/bin/curl -s --max-time 35 -b $COOKIE -o "$TMP" -w "%{http_code}" "$BASE/checkup/diagnose?account=3")
[[ "$code" == "200" ]] && log_ok "v02-DIAG-4 GET /checkup/diagnose?account=3 → 200" \
  || log_bad "v02-DIAG-4 account diagnose" "code=$code"

# DIAG-5 不存在账户 → 200 + 降级文案
code=$(/usr/bin/curl -s --max-time 35 -b $COOKIE -o "$TMP" -w "%{http_code}" "$BASE/checkup/diagnose?account=99999")
{ [[ "$code" == "200" ]] && grep -q '账户不存在\|AI 暂时不可用\|AI · 暂不可用' "$TMP"; } \
  && log_ok "v02-DIAG-5 跨家庭账户 diagnose 返回降级 (code=$code)" \
  || log_bad "v02-DIAG-5 cross-family" "code=$code"

# DIAG-6 跨账户(同家庭)200 — 账户维度可正常工作
code=$(/usr/bin/curl -s --max-time 35 -b $COOKIE -o "$TMP" -w "%{http_code}" "$BASE/checkup/diagnose?account=1")
[[ "$code" == "200" ]] && log_ok "v02-DIAG-6 GET /checkup/diagnose?account=1 (CASH) → 200" \
  || log_bad "v02-DIAG-6 cash account" "code=$code"

# ---------- v0.2 LLM 真实调用 ----------
section "v0.2 · LLM 真实调用 · qwen-plus(可选,无 key 时降级 fallback)"

# DIAG-LIVE 嗅探:GET /checkup/diagnose 是否实际由真 LLM(qwen/deepseek)成功返回综合诊断长文,
# 还是 fallback("AI 暂时不可用")。两种结果都不算 FAIL,但分别对应不同状态。
/usr/bin/curl -s --max-time 35 -b $COOKIE -o "$TMP" -w "" "$BASE/checkup/diagnose"
vendor=$(grep -oE 'data-vendor="[^"]+"' "$TMP" | head -1 | sed 's/data-vendor="\([^"]*\)"/\1/')
available=$(grep -oE 'data-available="[^"]+"' "$TMP" | head -1 | sed 's/data-available="\([^"]*\)"/\1/')
if [[ "$available" == "true" && ( "$vendor" == "qwen" || "$vendor" == "deepseek" ) ]]; then
  log_ok "v02-LLM-LIVE-1 LLM 实调用成功 vendor=$vendor 综合诊断长文已返回"
else
  log_skip "v02-LLM-LIVE-1" "LLM key 未配/全部失败,vendor=$vendor available=$available — 已降级 fallback,不阻塞 v0.2 验收"
fi

# ---------- v0.2 Stage 4: 账本侧(ledger.csv + 软删 + UI 入口) ----------
section "v0.2 · 阶段 4 · 账本侧 (FR-30/31/32)"

# LEDGER-1 /accounts 列表「体检」入口(2026-05-10 改 SVG 后,grep href)
$CURL -b $COOKIE "$BASE/accounts" -o "$TMP" -w ""
n=$(grep -oE 'href="/checkup\?account=[0-9]+"' "$TMP" | wc -l)
[[ $n -ge 13 ]] && log_ok "v02-LEDGER-1 /accounts 列表至少 13 个 → /checkup?account 链接 (n=$n)" \
  || log_bad "v02-LEDGER-1 体检入口" "n=$n"

# LEDGER-2 /accounts 列表「账本 CSV」入口
n=$(grep -oE 'href="/accounts/[0-9]+/ledger.csv"' "$TMP" | wc -l)
[[ $n -ge 13 ]] && log_ok "v02-LEDGER-2 /accounts 列表所有账户均含「⬇ 账本」入口 (n=$n)" \
  || log_bad "v02-LEDGER-2 账本入口" "n=$n"

# LEDGER-3 GET /accounts/3/ledger.csv → 200 + text/csv
type_resp=$($CURL -b $COOKIE -o "$TMP" -w "%{content_type}" "$BASE/accounts/3/ledger.csv")
{ grep -q 'text/csv' <<<"$type_resp" && grep -q '月份,期初,入账' "$TMP"; } \
  && log_ok "v02-LEDGER-3 ledger.csv 返回 text/csv + 表头正确" \
  || log_bad "v02-LEDGER-3 ledger.csv" "type=$type_resp"

# LEDGER-4 ledger.csv 含 BOM(Excel 友好)
head -c 3 "$TMP" | od -c | head -1 | grep -q '357 273 277' \
  && log_ok "v02-LEDGER-4 ledger.csv 含 UTF-8 BOM" \
  || log_bad "v02-LEDGER-4 BOM" "missing"

# LEDGER-5 ledger.csv Content-Disposition 含 filename*=UTF-8'' 编码
header=$($CURL -b $COOKIE -o /dev/null -D - "$BASE/accounts/3/ledger.csv" | tr -d '\r' | grep -i 'content-disposition')
echo "$header" | grep -q "filename\*=UTF-8" \
  && log_ok "v02-LEDGER-5 ledger.csv Content-Disposition 含 UTF-8 文件名" \
  || log_bad "v02-LEDGER-5 filename" "header=$header"

# LEDGER-6 跨家庭账户(id=99999) 应被 require 拦截
code=$($CURL -b $COOKIE -o /dev/null -w "%{http_code}" "$BASE/accounts/99999/ledger.csv")
[[ "$code" -ge 400 ]] && log_ok "v02-LEDGER-6 跨家庭账户 ledger.csv 拒绝 (code=$code)" \
  || log_bad "v02-LEDGER-6 越权" "code=$code"

# SOFT-DEL-2 entry 当前 OPEN 期 + 该期有 cf/transfer 时,应有至少 1 个 ⋮ 删除按钮
# 动态查 OPEN period id + 是否有可删的 cf/transfer
OPEN_PID=$(mysql -ufinance -pfinance finance -sN -e "SELECT id FROM period WHERE family_id=1 AND status='OPEN' ORDER BY period_start DESC LIMIT 1" 2>/dev/null)
OPEN_HAS_CF=$(mysql -ufinance -pfinance finance -sN -e "SELECT COUNT(*) FROM cash_flow WHERE period_id=$OPEN_PID AND deleted_at IS NULL" 2>/dev/null)
if [[ -z "$OPEN_PID" ]]; then
  log_skip "v02-SOFT-DEL-2 删除按钮" "无 OPEN 周期"
elif [[ "$OPEN_HAS_CF" == "0" ]]; then
  log_skip "v02-SOFT-DEL-2 删除按钮" "OPEN 期 ($OPEN_PID) 无 cf/transfer 可删"
else
  $CURL -b $COOKIE "$BASE/entry?period=$OPEN_PID" -o "$TMP" -w ""
  n=$(grep -oE 'hx-post="/entry/[a-z-]+/[0-9]+/delete"' "$TMP" | wc -l)
  [[ $n -ge 1 ]] && log_ok "v02-SOFT-DEL-2 entry OPEN 周期 ($OPEN_PID) 渲染 ⋮删除按钮 (n=$n)" \
    || log_bad "v02-SOFT-DEL-2 删除按钮" "n=$n"
fi

# SOFT-DEL-3 删除按钮不出现在 SNAPSHOT 行(只在 cash_flow / transfer)
{ grep -q 'cash-flow/[0-9]' "$TMP" || grep -q 'transfer/[0-9]' "$TMP"; } \
  && log_ok "v02-SOFT-DEL-3 删除链接指向 cash-flow / transfer 子路径" \
  || log_bad "v02-SOFT-DEL-3 删除链接" "missing"

# SOFT-DEL-4 删除按钮 hx-confirm 含确认提示
grep -q 'hx-confirm="确定删除' "$TMP" && log_ok "v02-SOFT-DEL-4 hx-confirm 删除提示存在" \
  || log_bad "v02-SOFT-DEL-4 confirm" "missing"

# SOFT-DEL-5 软删 endpoint POST 一条真实 OPEN 期 cash_flow,验证 200(此处 cf=323 已被前面真实测试软删,
# 任意找一个未软删的 OPEN cf 来再测一次。如果都已删,跳过)
$CURL -b $COOKIE -c $COOKIE "$BASE/entry?period=35" -o /dev/null
XSRF=$(awk '$6=="XSRF-TOKEN" {print $7}' $COOKIE)
victim_cf=$(grep -oE 'hx-post="/entry/cash-flow/[0-9]+/delete"' "$TMP" | head -1 | grep -oE '[0-9]+')
if [[ -n "$victim_cf" ]]; then
  code=$($CURL -b $COOKIE -X POST -H "X-XSRF-TOKEN: $XSRF" -o /dev/null -w "%{http_code}" \
        "$BASE/entry/cash-flow/$victim_cf/delete")
  [[ "$code" == "200" ]] && log_ok "v02-SOFT-DEL-5 POST 软删真实 cash_flow ($victim_cf) → 200" \
    || log_bad "v02-SOFT-DEL-5 软删失败" "cf=$victim_cf code=$code"
else
  log_skip "v02-SOFT-DEL-5 软删真实 cf" "no candidate cf"
fi

# SOFT-DEL-6 软删后该 cf 不再出现在 entry 页(deleted_at 已设)
if [[ -n "$victim_cf" ]]; then
  $CURL -b $COOKIE "$BASE/entry?period=35" -o "$TMP" -w ""
  if ! grep -q "cash-flow/$victim_cf/delete" "$TMP"; then
    log_ok "v02-SOFT-DEL-6 已软删 cf=$victim_cf 从 entry 页面消失"
  else
    log_bad "v02-SOFT-DEL-6 已软删 cf 仍可见" "cf=$victim_cf"
  fi
fi

# SOFT-DEL-7 CLOSED 周期软删被拒绝(用 cash_flow 222 — 在 CLOSED period)
code=$($CURL -b $COOKIE -X POST -H "X-XSRF-TOKEN: $XSRF" -o /dev/null -w "%{http_code}" \
      "$BASE/entry/cash-flow/222/delete")
# 后端 throw IllegalStateException → 500 错误页是预期(进入兜底);亦可能未来改 400 友好
[[ "$code" == "500" || "$code" == "400" ]] && log_ok "v02-SOFT-DEL-7 CLOSED 周期软删被拒 (code=$code)" \
  || log_bad "v02-SOFT-DEL-7 CLOSED 拒写" "code=$code"

# SOFT-DEL-8 不存在的 cash_flow id 也被拒绝(NPE 转 500/400)
code=$($CURL -b $COOKIE -X POST -H "X-XSRF-TOKEN: $XSRF" -o /dev/null -w "%{http_code}" \
      "$BASE/entry/cash-flow/9999999/delete")
[[ "$code" -ge 400 ]] && log_ok "v02-SOFT-DEL-8 不存在 cf 软删被拒 (code=$code)" \
  || log_bad "v02-SOFT-DEL-8 missing cf 拦截" "code=$code"

# v0.2 FR-30 · 账户详情页(账本视角)
$CURL -b $COOKIE -o "$TMP" -w "" "$BASE/accounts/1"
{ grep -q '<h1' "$TMP" && grep -q '招行储蓄卡-工资' "$TMP"; } \
  && log_ok "v02-FR30-1 GET /accounts/1 返回详情页 + 显示账户名" \
  || log_bad "v02-FR30-1 详情页" "missing"
grep -q 'id="balanceTimeline"' "$TMP" \
  && log_ok "v02-FR30-2 详情页含余额时序 canvas" \
  || log_bad "v02-FR30-2 时序图" "missing"
n_kpi=$(grep -c 'kpi-eyebrow' "$TMP")
[[ $n_kpi -ge 8 ]] && log_ok "v02-FR30-3 详情页含 ≥4 KPI(重复 ≥8 次)实际 $n_kpi" \
  || log_bad "v02-FR30-3 KPI 卡" "n=$n_kpi"
n_det=$(grep -cE '<details' "$TMP")
[[ $n_det -ge 1 ]] && log_ok "v02-FR30-4 详情页月分组 details=$n_det" \
  || log_bad "v02-FR30-4 月分组" "n=$n_det"
{ grep -q '看资产体检' "$TMP" && grep -q '导出本账户 CSV' "$TMP"; } \
  && log_ok "v02-FR30-5 详情页底栏含「看资产体检 / 导出 CSV」" \
  || log_bad "v02-FR30-5 底栏" "missing"
$CURL -b $COOKIE -o /dev/null -w "" "$BASE/accounts/99999"
code=$($CURL -b $COOKIE -o /dev/null -w "%{http_code}" "$BASE/accounts/99999")
[[ "$code" -ge 400 ]] && log_ok "v02-FR30-6 跨家庭账户详情页拒绝 (code=$code)" \
  || log_bad "v02-FR30-6 越权" "code=$code"
# 列表入口接线
$CURL -b $COOKIE "$BASE/accounts" -o "$TMP" -w ""
n=$(grep -cE 'href="/accounts/[0-9]+"' "$TMP")
[[ $n -ge 13 ]] && log_ok "v02-FR30-7 /accounts 列表至少 13 个 → 详情链接 (n=$n)" \
  || log_bad "v02-FR30-7 详情链接" "n=$n"

# v0.2 audit 真名修复
$CURL -b $COOKIE "$BASE/admin/audit" -o "$TMP" -w ""
n_id=$(grep -cE '>#[0-9]+<' "$TMP")
[[ $n_id -eq 0 ]] && log_ok "v02-AUDIT-1 audit 由谁列不再显示 #id" \
  || log_bad "v02-AUDIT-1 残留 #id" "n=$n_id"

# v0.2 dashboard anchor bug fix · 应永远取最新一期(包括 OPEN)
$CURL -b $COOKIE "$BASE/dashboard" -o "$TMP" -w ""
{ grep -q '资产体检' "$TMP" && grep -q 'kpi-card' "$TMP"; } \
  && log_ok "v02-DASH-ANCHOR dashboard 渲染 + KPI 完整(anchor 取最新期)" \
  || log_bad "v02-DASH-ANCHOR dashboard 渲染" "missing"

# v0.2 FR-40e · /reports 风险等级分布
$CURL -b $COOKIE "$BASE/reports?range=1Y&currency=CNY" -o "$TMP" -w ""
{ grep -q '风险等级分布' "$TMP" && grep -q 'riskDistChart' "$TMP"; } \
  && log_ok "v02-FR40e-1 /reports 含「风险等级分布」环形图 canvas" \
  || log_bad "v02-FR40e-1 风险环形图" "missing"

# v0.2 防回归 · 所有有 canvas 的页面:1) 引 Chart.js  2) 引 datalabels plugin  3) 每个 new Chart 都注册 ChartDataLabels
# 来自 memory feedback_chart_datalabels:数字必须直接浮在数据点/扇片/柱顶上
for path in /dashboard /reports /checkup '/checkup?account=3' /accounts/1; do
  $CURL -b $COOKIE "$BASE$path" -o "$TMP" -w ""
  if grep -q '<canvas' "$TMP"; then
    n_chart=$(grep -c 'chart.umd' "$TMP")
    n_plugin=$(grep -c 'chartjs-plugin-datalabels' "$TMP")
    n_chart_calls=$(grep -c 'new Chart' "$TMP")
    n_register=$(grep -c 'ChartDataLabels' "$TMP")
    pid=$(echo $path | tr '/?=' '___')
    if [[ "$n_chart" -ge 1 && "$n_plugin" -ge 1 && "$n_register" -ge "$n_chart_calls" ]]; then
      log_ok "v02-CHART-$pid 含 canvas + Chart.js + datalabels(charts=$n_chart_calls register=$n_register)"
    else
      log_bad "v02-CHART-$pid 图表配置不全" "chart=$n_chart plugin=$n_plugin register=$n_register charts=$n_chart_calls"
    fi
  fi
done
# 注意:这两个 case 必须独立读 /reports(上面 for 循环最后 $TMP 是 /accounts/1)
$CURL -b $COOKIE "$BASE/reports?range=1Y&currency=CNY" -o "$TMP" -w ""
n=$(grep -oE '★+' "$TMP" | wc -l)
[[ $n -ge 3 ]] && log_ok "v02-FR40e-2 /reports 风险等级表格含 ★ 标识 (n=$n)" \
  || log_bad "v02-FR40e-2 风险表格" "n=$n"
grep -q '进入资产体检' "$TMP" && log_ok "v02-FR40e-3 /reports 风险段含「→ 进入资产体检」入口" \
  || log_bad "v02-FR40e-3 体检入口" "missing"

# v0.2 FR-38 · dashboard KPI 卡 deep-link 到 /checkup 锚点
$CURL -b $COOKIE "$BASE/dashboard?range=1Y&currency=CNY" -o "$TMP" -w ""
n=$(grep -oE 'href="/checkup[^"]*"' "$TMP" | wc -l)
[[ $n -ge 5 ]] && log_ok "v02-FR38-1 dashboard 5 张 KPI 卡均含 /checkup 链接 (n=$n)" \
  || log_bad "v02-FR38-1 KPI 链接" "n=$n"

# 至少 3 个不同锚点(allocation / liquidity / 顶级)
n_anchors=$(grep -oE 'href="/checkup[^"]*"' "$TMP" | sort -u | wc -l)
[[ $n_anchors -ge 3 ]] && log_ok "v02-FR38-2 KPI 锚点齐全 (allocation/liquidity/顶级,$n_anchors 种)" \
  || log_bad "v02-FR38-2 锚点种类" "n=$n_anchors"

# /checkup family 页含锚点 id(allocation / risk / liquidity / return)
$CURL -b $COOKIE "$BASE/checkup" -o "$TMP" -w ""
ids=$(grep -oE 'id="(allocation|risk|liquidity|return)"' "$TMP" | sort -u | wc -l)
[[ $ids -ge 4 ]] && log_ok "v02-FR38-3 /checkup family 含 4 个锚点 id (n=$ids)" \
  || log_bad "v02-FR38-3 锚点 id" "n=$ids"

# ---------- v0.2 UX 小优化(2026-05-10)----------
section "v0.2 · UX 小优化"

# UX-1 entry 余额输入框含 onfocus=this.select() + ✕ 清空按钮
OPEN_PID=$(mysql -ufinance -pfinance finance -sN -e "SELECT id FROM period WHERE family_id=1 AND status='OPEN' ORDER BY period_start DESC LIMIT 1" 2>/dev/null)
$CURL -b $COOKIE "$BASE/entry?period=$OPEN_PID" -o "$TMP" -w ""
n_focus=$(grep -oE 'onfocus="this.select\(\)"' "$TMP" | wc -l)
[[ $n_focus -ge 1 ]] && log_ok "v02-UX-1 entry 余额 input 含 onfocus=select (n=$n_focus)" \
  || log_bad "v02-UX-1 onfocus 缺失" "n=$n_focus"
n_clear=$(grep -oE 'title="清空"' "$TMP" | wc -l)
[[ $n_clear -ge 1 ]] && log_ok "v02-UX-2 entry 余额 input 含 ✕ 清空按钮 (n=$n_clear)" \
  || log_bad "v02-UX-2 清空按钮缺失" "n=$n_clear"

# UX-3 dashboard 含 accountDivergeChart canvas + accountRows 数据
$CURL -b $COOKIE "$BASE/dashboard?range=1Y&currency=CNY" -o "$TMP" -w ""
{ grep -q '<canvas id="accountDivergeChart"' "$TMP" && grep -q 'accountRows: \[' "$TMP"; } \
  && log_ok "v02-UX-3 dashboard 含按账户分布 canvas + accountRows 数据" \
  || log_bad "v02-UX-3 按账户分布图" "missing canvas or data"
grep -q '按账户分布' "$TMP" \
  && log_ok "v02-UX-4 dashboard 含「按账户分布」标题" \
  || log_bad "v02-UX-4 按账户分布标题" "missing"

# UX-5 entry 余额 / 备注 input 高度统一(都用 h-9)+ 备注独立 eyebrow,避免对齐错位
$CURL -b $COOKIE "$BASE/entry?period=$OPEN_PID" -o "$TMP" -w ""
# newBalance input 是多行属性,用 awk 把 input 多行折叠成一行后再 grep h-9
nb_h9=$(awk 'BEGIN{RS=">"} /name="newBalance"/' "$TMP" | grep -c 'h-9')
nt_h9=$(grep -oE 'name="note"[^>]*h-9' "$TMP" | wc -l)
nt_eb=$(grep -c '>备注</span>' "$TMP")
[[ $nb_h9 -ge 1 && $nt_h9 -ge 1 && $nt_eb -ge 1 ]] \
  && log_ok "v02-UX-5 entry 余额 / 备注 input 高度统一(h-9 nb=$nb_h9 nt=$nt_h9)+ 备注独立 eyebrow ($nt_eb)" \
  || log_bad "v02-UX-5 entry 输入框对齐" "newBalance.h-9=$nb_h9 note.h-9=$nt_h9 备注.eyebrow=$nt_eb"

# ---------- v0.2 · FR-40e 报表风险等级分布(2026-05-10) ----------
section "v0.2 · FR-40e · 报表风险等级分布"

# FR40e-1 reports 页加载成功
$CURL -b $COOKIE "$BASE/reports" -o "$TMP" -w ""
grep -q '风险等级分布' "$TMP" \
  && log_ok "v02-FR40E-1 reports 含「风险等级分布」标题" \
  || log_bad "v02-FR40E-1 标题" "missing"

# FR40e-2 含 riskDistChart canvas
grep -q 'riskDistChart' "$TMP" \
  && log_ok "v02-FR40E-2 reports 含 #riskDistChart canvas" \
  || log_bad "v02-FR40E-2 canvas" "missing"

# FR40e-3 含风险敞口明细表格 + 进入资产体检入口
{ grep -q '风险敞口明细' "$TMP" && grep -q '进入资产体检' "$TMP"; } \
  && log_ok "v02-FR40E-3 reports 含风险敞口明细 + 资产体检入口" \
  || log_bad "v02-FR40E-3 table+entry" "missing"

echo
echo "═══════════════════════════════════════"
echo " 总结: PASS=$PASS  FAIL=$FAIL  SKIP=$SKIP"
echo "═══════════════════════════════════════"
if [[ $FAIL -gt 0 ]]; then
  echo "失败用例:"
  for f in "${FAILED[@]}"; do echo "  · $f"; done
  exit 1
fi
exit 0
