# 家庭账房 v0.1 · QA 测试用例

> 基于 `prd/v0.1.md` 与 `tech-design/v0.1.md`,以可执行黑盒测试视角拆解 22 条 FR + 认证。
> 每条用例:**ID · 一句话目标 · 操作步骤 · 预期 · 实际(执行后填)**。
> 跑测脚本:`bash /tmp/qa-run.sh`(用 curl + grep 校验 HTML 结构与副作用)。

## 0 · 认证(基础)

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| AUTH-1 | 未登录访问受限页跳登录 | GET /dashboard 不带 cookie | 302 → /login |
| AUTH-2 | 登录页可见 | GET /login | 200 + 含 `_csrf`、含 `username`/`password` 输入框 |
| AUTH-3 | 错误密码失败 | POST /login wrong | 302 → /login?error |
| AUTH-4 | 正确密码登录成功 | POST /login diwa/demo1234 | 302 → / |
| AUTH-5 | 登录后访问 /dashboard 完整 HTML | GET /dashboard | 200,以 `</html>` 结束 |
| AUTH-6 | 登出清 cookie | POST /logout | 302 → /login?logout |
| AUTH-7 | /health 公开 JSON | GET /health(无 cookie) | 200 `{"status":"UP"}` |

## FR-1 · 家庭与成员

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR1-1 | /admin/family 200 | GET /admin/family | 200,含家庭名、品牌名、本位币、周期类型 |
| FR1-2 | /admin/members 200 | GET /admin/members | 200,显示 2 个成员 |
| FR1-3 | 编辑家庭名 | POST /admin/family name=测试家 | 302 → /admin/family;DB 更新 |
| FR1-4 | 编辑成员显示名 | POST /admin/members/{id} | 302;DB 更新 |
| FR1-5 | 重置密码 | POST /admin/members/{id}/reset-password | 显示一次性临时密码 |
| FR1-6 | logo 字段在表单 | GET /admin/family | 含 logo upload form;family.logoPath=NULL 时显示默认 SVG |
| FR1-7 | 添加成员入口存在 | GET /admin/members | 含 "+ 添加成员" 按钮 + 弹层 form |
| FR1-8 | 改密页可访问 | GET /profile/password | 200,含 "新密码" 输入,显示"显示/隐藏密码"按钮 |
| FR1-9 | 强制改密拦截 | DB 设 mustChangePw=1 后 GET /dashboard | 302 → /profile/password |
| FR1-10 | 默认 logo 兜底 | DELETE 物理 logo 文件 后 GET /dashboard | nav 仍显示默认 SVG(浏览器 onerror) |

## FR-2 · 账户模板向导

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR2-1 | /accounts/new 弹向导 | GET /accounts/new | 200,含 `添加账户向导`,模板列表显示 ≥ 12 个 |
| FR2-2 | /admin/account-templates 200 | GET /admin/account-templates | 200,显示模板列表只读 |
| FR2-3 | 模板下拉中文化 | GET /accounts/new | type 选项含 `现金 (CASH)` 等中文格式 |

## FR-3 · 账户管理

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR3-1 | /accounts 列表 | GET /accounts | 200,显示所有未归档账户 |
| FR3-2 | 新建账户 | POST /accounts | 302 → /accounts;新增 1 行 |
| FR3-3 | 编辑专属页 | GET /accounts/{id}/edit | 200,标题"编辑账户:XXX",按钮"保存对账户的修改" |
| FR3-4 | 编辑提交 | POST /accounts/{id}/edit | 302 → /accounts;DB 更新 |
| FR3-5 | 归档 | POST /accounts/{id}/archive | 302;archived_at 写入 |
| FR3-6 | 查看归档列表 | GET /accounts?archived=true | 含归档账户 |
| FR3-7 | 恢复归档 | POST /accounts/{id}/restore | archived_at 清空 |

## FR-4 · 周期配置

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR4-1 | 切换 period_type 阻塞 | OPEN 周期下 POST 切换 | flash 阻塞提示 |
| FR4-2 | period_type 显示当前 | GET /admin/family | 显示 MONTHLY/WEEKLY |

## FR-5 · 周期与待办自动生成

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR5-1 | 当前 OPEN 周期存在 | DB SELECT period status=OPEN | 1 行 |
| FR5-2 | 待办行数 = 未归档账户数 | DB count snapshot_todo / account active | 相等 |

## FR-6 · 待办与全员视图

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR6-1 | /my-todos 200 | GET /my-todos | 200,显示当期 PENDING 行 |
| FR6-2 | /my-todos→/entry 带账户 | 链接 href 含 `account=` | true |
| FR6-3 | mine=true 行数减少 | GET /entry?mine=true | size < /entry?mine=false |
| FR6-4 | account 筛选生效 | GET /entry?account=1 | 仅 1 个 entry-row,显示"已按账户筛选" |

## FR-7 · 余额录入

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR7-1 | /entry 默认显示行 | GET /entry | rows ≥ 1 |
| FR7-2 | 提交新余额 | POST /entry/{id}/balance newBalance=... | 200 (HTMX fragment),DB 写入 period_snapshot |
| FR7-3 | 已填 ✓ 状态切换 | 提交后再 GET /entry | 该账户行变 ✓ |
| FR7-4 | 未解释金额提示 | 不平衡时 | 显示"未解释" + 引导按钮 |
| FR7-5 | 本期流水明细列表 | 展开 row | 显示"本期流水 · N 笔",含 SNAPSHOT/INCOME/EXPENSE/TRANSFER_IN/OUT 5 类按时间排序 |
| FR7-6 | 不分页约束 | 单账户单期 < 30 条 | 全量列出,无 paging |
| FR7-7 | 进入页面输入框预填上期值 | GET /entry?account=X(snapshot 不存在)| `<input name="newBalance">` 的 value 等于上期末 |
| FR7-8 | 快捷+收入累加余额 | 上期 10000,POST cash-flow INCOME 100 | snapshot=10100;cash_flow +1;收入字段 100 |
| FR7-9 | 快捷-支出累加余额 | 续上,POST cash-flow EXPENSE 1000 | snapshot=9100;cash_flow +1;支出字段 1000 |
| FR7-10 | 校准余额直接覆盖 | 续上,POST balance=4000 | snapshot=4000(覆盖);unexplained=−5100 |
| FR7-11 | 校准后再叠加快捷 | 续上,POST cash-flow INCOME 200 | snapshot=4200;收入字段 300;unexplained 仍 −5100 |
| FR7-12 | 划转两端联动 | POST /entry/{A}/transfer toAccountId=B amount=500 | A snapshot -=500;B snapshot +=500;两端均 ✓ |
| FR7-13 | HX-Trigger refresh 链路 | POST 后 response 头 | 含 `HX-Trigger: refresh-row-{accountId}`;转账时还含 to 端的 trigger |

## FR-8 · 现金流

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR8-1 | 提交收入 | POST /entry/{id}/cash-flow kind=INCOME | 200,DB cash_flow 写入 |
| FR8-2 | 提交支出 | POST kind=EXPENSE | DB 写入 |

## FR-9 · 转账

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR9-1 | 提交转账 | POST /entry/{id}/transfer | DB transfer 写入 |
| FR9-2 | 24h 重复检测 | 同 (from,to,amount,period) 二次提交 | 二次确认 |

## FR-10 · 智能转账推断

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR10-1 | |未解释| > 3000 提示 | EntryRow.suggestTransfer = true | UI 显示 `💡 看起来像账户间转账?` |

## FR-11 · 周期关闭 + 重算

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR11-1 | 提交本期成员完成 | POST /entry/{periodId}/complete | 写 period_member_completion |
| FR11-2 | 全员完成自动 CLOSED | 所有成员都 complete | period.status=CLOSED |
| FR11-3 | metrics_recompute_log 写入 | CLOSED 后 | 1 行 |
| FR11-4 | CLOSED 期点 +/-/划转 | POST /entry/{closedAcc}/cash-flow | 200 + HX-Trigger=showToast(toast 拒写) |
| FR11-5 | 强制关账(代填上期末)| POST /admin/periods/{id}/force-close | period CLOSED;PENDING=0;snapshot N 行(=未归档账户数);metrics_recompute_log +1 |

## FR-12 · 周期重开

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR12-1 | /admin/periods 列出 | GET | 含周期 + 状态 |
| FR12-2 | CLOSED 重开 | POST /admin/periods/{id}/reopen reason=test | 302;period_reopen_log 写入;status=OPEN |
| FR12-3 | 重开 reason 必填 | reason 空 | 阻塞或 400 |
| FR12-4 | 立即开下一周期(测试用)| POST /admin/periods/open-next | 302 → /admin/periods;新 period.status=OPEN;snapshot_todo N 条(=未归档账户) |
| FR12-5 | OPEN 周期状态视觉绿色 | GET /admin/periods | OPEN 文案"OPEN · 进行中" + forest 配色;CLOSED "CLOSED · 已结束" + rust 配色 |
| FR12-6 | 开账时所有账户自动延续上期末 | POST /admin/periods/open-next | 新 period 的 period_snapshot 行数 = 未归档账户数;每行 note="开账自动延续上期末余额 X" |
| FR12-7 | LOAN 开账时按差值预填 | POST /admin/periods/open-next | LOAN 账户 snapshot.end_balance = prev + (prev - prevPrev);snapshot_todo.prefilled_balance 同 |

## FR-13 · Dashboard

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR13-1 | 5 KPI 卡可见 | GET /dashboard | 含 净资产/总资产/总负债/紧急储备/负债率 |
| FR13-2 | range tabs 切换不返回 fragment | GET /dashboard?range=1M(无 HX-Request) | 完整 HTML(`</html>`) |
| FR13-3 | range tabs HTMX 返回 fragment | GET 带 HX-Request | 仅 `<div id=dashboard-region>` |
| FR13-4 | YTD/3M/6M/ALL 都不抛错 | GET 各 range | 200 + 完整 HTML |
| FR13-5 | 红 banner 显示 pending | DB 有未填 + GET | 显示"本期还有 X 个账户未填" |

## FR-14 · 报表

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR14-1 | /reports 200 | GET /reports | 200,含家庭 XIRR/TWR、账户级表 |
| FR14-2 | range tabs 完整 | GET /reports?range=YTD | 完整 HTML |
| FR14-3 | 汇率明细表显示 | GET /reports | 含 fx_rate 表行 |

## FR-15 · 多币种

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR15-1 | /admin/fx 200 | GET | 含 USD/HKD/CNY 行 |
| FR15-2 | 手填覆盖 | POST /admin/fx | DB 写入 |

## FR-16 · CSV 导出

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR16-1 | /export.zip 200 | GET | 200,Content-Type octet-stream |
| FR16-2 | ZIP 含 8 CSV + README | unzip -l | 9 文件齐全 |
| FR16-3 | UTF-8 BOM | 头 3 字节 | EF BB BF |

## FR-17 · 站内提醒

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR17-1 | banner 显示 pendingRows | dashboard pending banner 元素 | 存在 |

## FR-18 · 备份

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR18-1 | /admin/backup 200 | GET | 200,展示最近备份状态 |

## FR-19 · LOAN 专属

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR19-1 | LOAN 余额负数显示绝对值 | dashboard 房贷行 | "¥XX,XXX" 不带负号 |
| FR19-2 | 资产配置不含 LOAN | dashboard | allocation labels 不含 LOAN |
| FR19-3 | LOAN 编辑页有还款来源字段 | GET /accounts/{loanId}/edit | 含"默认还款来源" |

## FR-20 · /admin

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR20-1~10 | 11 个 admin 子页全部 200 | GET 各路由 | 200 + 完整 HTML |

## FR-21 · 账户筛选器

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR21-1 | accounts=ID 参数生效 | GET /dashboard?accounts=1 | KPI 反映只此账户 |
| FR21-2 | 默认全选 | GET /dashboard | 显示"X 个已选" |
| FR21-3 | 多选 form | GET /dashboard 展开筛选 | 含 `<input type="checkbox" name="accounts">` + "应用筛选"按钮 |
| FR21-4 | 多选提交 | GET /dashboard?accounts=1&accounts=2&accounts=3 | "3 个已选";KPI/图表反映 3 个账户合计 |
| FR21-5 | 全选/全清/重置按钮 | 模板 | 三个按钮均存在 |
| FR21-6 | 账户类型筛选 | GET /accounts?type=CASH | 列表只剩 CASH,选中类型 pill 高亮 |

## FR-22 · 显示币种切换

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR22-1 | 切 USD 货币符号变 $ | GET /dashboard?currency=USD | 含 `$` |
| FR22-2 | 切 HKD 货币符号变 HK$ | GET /dashboard?currency=HKD | 含 `HK$` |

## 静态资源 / 安全

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| ST-1 | /vendor/tailwind.js 200 | GET 无 cookie | 200 |
| ST-2 | /vendor/htmx.min.js 200 | GET 无 cookie | 200 |
| ST-3 | /vendor/chart.umd.min.js 200 | GET 无 cookie | 200 |
| ST-4 | /vendor/echarts.min.js 200 | GET 无 cookie | 200 |
| ST-5 | /css/style.css 200 | GET 无 cookie | 200 |
| ST-6 | CSRF 拒绝无 token POST | POST /accounts 不带 token | 403 |
| ST-7 | favicon SVG 头 200 + immutable | curl -I /img/default-logo.svg | 200 + Cache-Control immutable |
| ST-8 | 全局 loading 元素首屏注入 | GET /dashboard | HTML 含 page-progress / page-overlay / seal-character / seal-ink-dot |
| ST-9 | LOAN 余额修改不联动 | DB 改 LOAN snapshot 后查 transfer 表 | 不应有 from default_payment_source 的新 transfer |
| ST-10 | reports/ALL 不再 500 | GET /reports?range=ALL | 200 + 完整 HTML(NaN-safe) |
| ST-11 | block fragment 整块 swap | POST /entry/{id}/cash-flow | response 含 entry-block-{id} + entry-row-{id} + 展开本期流水(HX-Reswap=outerHTML 整块刷新) |
| ST-12 | 手动刷新 icon 存在 | GET /entry?account={id} | 含 `aria-label="刷新"` + `⟳` 字符 |
| ST-13 | dashboard 实时自刷新 | GET /dashboard | dashboard-region 含 hx-trigger="visibilitychange... every 90s" |

总计:**78 用例**(v0.1)。

---

## v0.2 · FR-33 微信引导 + FR-34 iOS PWA 添加到主屏

> 2026-05-09 上线。新增 10 条自动化(已加入 `/tmp/qa-run.sh` 末段)+ 8 条真机手测。

### v0.2 · 自动化(curl,与 v0.1 同 BASE)

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR34-1 | manifest MIME 正确 | curl -I /manifest.webmanifest | Content-Type=application/manifest+json |
| FR34-2 | manifest 字段齐 | 解析 JSON | 含 name / start_url=/dashboard / display=standalone / icons[3] |
| FR34-3 | 三张 PNG 200 | curl /img/{apple-touch-icon-180,icon-192,icon-512}.png | 各 200 + Content-Type=image/png |
| FR34-4 | layout 含 PWA meta | curl /login → grep | 含 apple-mobile-web-app-capable / status-bar-style / title / manifest link / apple-touch-icon-180.png / theme-color |
| FR34-5 | mobile-guide.js 未登录可达 | curl /js/mobile-guide.js(无 cookie) | 200 |
| FR34-6 | manifest 未登录可达 | curl /manifest.webmanifest(无 cookie) | 200 |
| FR33-1 | layout 引用 mobile-guide.js | curl /login → grep mobile-guide.js | 命中 |
| FR33-2 | 脚本含微信 + iOS 检测分支 | curl /js/mobile-guide.js → grep | 含 MicroMessenger / wx_dismissed_at / pwa_dismissed_at / standalone |

### v0.2 · 真机手测(浏览器开发者工具 / 实机)

| ID | 设备 | 步骤 | 预期 |
|---|---|---|---|
| FR33-M1 | iOS 微信 | 微信里点账房链接 | 看到全屏遮罩 + 引导卡 + 大箭头指右上 ⋯;呼吸闪动 |
| FR33-M2 | iOS 微信 | 点 dismiss 后立即重进 | 不再弹遮罩(localStorage `wx_dismissed_at` 已写) |
| FR33-M3 | Android 微信 | 同 M1 | 行为一致 |
| FR34-M1 | iPhone Safari | 打开 dashboard | 1.5 秒后底部弹卡片;高亮分享按钮金色光环 |
| FR34-M2 | iPhone Safari | 按引导:分享 → 添加到主屏 → 添加 | 主屏出现**墨底金棕"账"硬币 ¥ + 朱红印泥点**(非默认 favicon、非截图);点击进入是 standalone(无 Safari UI) |
| FR34-M3 | iPhone(主屏入口) | 从主屏图标重开账房 | banner 不再弹(`navigator.standalone === true`) |
| FR34-NEG-1 | macOS Chrome / Firefox / Edge | 打开账房 | banner 不弹、遮罩不弹 |
| FR34-NEG-2 | iOS Chrome / Firefox(CriOS / FxiOS) | 打开账房 | 都不弹(不是 Safari) |

### v0.2 · 自动化测试结果(2026-05-09)

```
═══════════════════════════════════════
 总结: PASS=88  FAIL=0  SKIP=1
═══════════════════════════════════════
```

v0.1 78 条用例继续 PASS;v0.2 新增 10 条全 PASS;无回归。
