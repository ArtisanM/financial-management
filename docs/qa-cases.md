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
| AUTH-4 | 正确密码登录成功 | POST /login zhangwei/demo1234 | 302 → / |
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
| FR1-6 | logo 字段在表单 | GET /admin/family | 含 logo upload form |

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

## FR-12 · 周期重开

| ID | 目标 | 步骤 | 预期 |
|---|---|---|---|
| FR12-1 | /admin/periods 列出 | GET | 含周期 + 状态 |
| FR12-2 | CLOSED 重开 | POST /admin/periods/{id}/reopen reason=test | 302;period_reopen_log 写入;status=OPEN |
| FR12-3 | 重开 reason 必填 | reason 空 | 阻塞或 400 |

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

总计:**70 + 用例**。
