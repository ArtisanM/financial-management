# LLM API Key 申请与配置指南

本文档面向**部署本应用的家庭管理员**(也就是你),用于配置"资产体检"模块所需的 LLM API Key。

应用代码已经写死了端点和模型 ID,你**不需要改任何代码**,只需要去厂商那里申请 API Key,然后填进 `/etc/finance.env` 即可。

---

## 1. 需要准备什么

本应用采用**主备双模型架构**,主模型用阿里云 Qwen-Plus,备用模型用 DeepSeek-Chat。

| 项目 | 主模型:阿里云 Qwen-Plus | 备模型:DeepSeek-Chat |
|---|---|---|
| 厂商 | 阿里云(百炼 / Model Studio) | 杭州深度求索 |
| 控制台域名 | bailian.console.aliyun.com | platform.deepseek.com |
| 需要的凭证 | **1 个 API Key**(以 `sk-` 开头) | **1 个 API Key**(以 `sk-` 开头) |
| 是否需要 sk 配对(AccessKey + Secret) | **不需要**。OpenAI-compatible 模式只需要单个 API Key,不要去翻 RAM 控制台找 AK/SK | **不需要**。单 API Key 即可 |
| 是否需要实名认证 | **需要**。中国大陆个人开发者注册后必须做支付宝/银行卡实名,否则不给调用大模型 | **需要**。手机号 + 实名 |
| 是否需要绑定支付方式 | **需要**绑卡。但有 7000 万 token 免费额度(新用户 90 天有效),家庭场景实际几乎用不掉 | **需要**充值。最低充值 ¥1,但建议充 ¥10 起 |
| 推荐首次充值额度 | **¥0**(先吃免费额度,扛个一两年没问题) | **¥10**(够用好几年) |
| 预估月成本(家庭场景) | **< ¥0.20**(详见第 6 节) | **几乎为 0**(只在主模型挂掉时才走 fallback) |
| 模型 ID(代码已写死,仅供参考) | `qwen-plus` | `deepseek-chat` |
| 调用端点(代码已写死,仅供参考) | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `https://api.deepseek.com/v1` |

> **为什么主模型选 qwen-plus 而非 qwen-turbo**:资产体检的建议文案对**数字保真**要求极高(原文「应急储备 2.1 个月」绝不能被 LLM 改写成「约 2 个月」「两个月」)。qwen-turbo 在强约束 prompt + 数字逐字符保留任务上偶有失误,qwen-plus 表现稳定。代价是单价高 3 倍左右,但家庭场景一年总成本仍 < ¥3,值得。

> 关于 `deepseek-chat`:截至 2026-05-10,这个模型 ID 仍可用,但 DeepSeek 官方公告会在 **2026-07-24** 将其作为 `deepseek-v4-flash` 非思考模式的兼容别名最终废弃。本应用后续会跟进升级到 `deepseek-v4-flash`,届时会更新代码。在那之前你申请的 key 不变,只是模型 ID 变了。

---

## 2. 怎么获取(分步骤)

### 2.1 阿里云 Qwen API Key (主模型,**必配**)

1. **注册阿里云账号**
   - 打开 <https://www.aliyun.com/>,右上角"注册"
   - 用手机号注册,绑定支付宝(必须)

2. **完成实名认证**
   - 进入 <https://account.console.aliyun.com/>(账号中心)
   - 左侧菜单找"实名认证",选"个人实名",用支付宝授权一键完成

3. **开通百炼大模型服务**
   - 打开 <https://bailian.console.aliyun.com/>
   - 首次进入会弹"开通服务"协议,勾选同意 → 开通(免费,只是开通,不扣钱)
   - 开通后会自动赠送新人 token 包

4. **创建 API Key**
   - 在百炼控制台,点击右上角头像旁边的钥匙图标,或直接打开 <https://bailian.console.aliyun.com/?tab=model#/api-key>
   - 页面顶部按钮"**创建我的 API-KEY**" → 选择业务空间(默认 default 即可) → 填描述比如"家庭资产管理 web app" → 创建
   - 弹窗显示完整的 `sk-xxxxxxxxxxxxxxxx`(**只显示一次**),立刻拷贝出来。如果忘记拷贝,只能再创建一个新的

5. **(可选)充值**
   - 7000 万免费 token 用完之前都不用充,家庭场景大概率永远用不完
   - 真要充的话:控制台右上角"费用"→"充值",最低 ¥10

### 2.2 DeepSeek API Key (备用模型,**可选**)

1. **注册 DeepSeek 账号**
   - 打开 <https://platform.deepseek.com/>
   - 右上角"登录/注册",手机号验证码注册即可

2. **完成实名(如果系统要求)**
   - 个人 API 用户一般不需要单独实名,手机号即视为实名
   - 如果触发风控要求补充身份证,按页面引导走支付宝授权即可

3. **充值**
   - 进入 <https://platform.deepseek.com/top_up> 或左侧菜单"充值"
   - 支持微信/支付宝,最低 ¥1。**建议直接充 ¥10**,够这个 app 跑好几年

4. **创建 API Key**
   - 打开 <https://platform.deepseek.com/api_keys>
   - 点页面中央"**创建 API Key**" 按钮 → 给个名字比如 `finance-app-fallback` → 创建
   - 弹窗显示完整的 `sk-xxxxxxxxxxxxxxxx`(**只显示一次**),立刻拷贝。失误只能重新建

---

## 3. 配置方式

把上面拿到的两个 key 写进服务器上的 `/etc/finance.env`:

```bash
sudo vi /etc/finance.env
```

加这两行(等号两边**不要空格**,值不要加引号):

```
FINANCE_LLM_QWEN_API_KEY=sk-你刚才从阿里云拷贝的那一长串
FINANCE_LLM_DEEPSEEK_API_KEY=sk-你刚才从-DeepSeek-拷贝的那一长串
```

文件权限必须收紧(包含敏感凭证):

```bash
sudo chown root:finance /etc/finance.env
sudo chmod 640 /etc/finance.env
```

> `640` = root 可读写、finance 用户组只读、其他用户无权访问。本应用 systemd unit 以 `finance` 用户运行,正好能读到。

重启服务让新环境变量生效:

```bash
sudo systemctl restart finance
sudo systemctl status finance      # 看是否 active (running)
sudo journalctl -u finance -n 50   # 看启动日志,确认没有 "missing API key" 之类的报错
```

---

## 4. 建议

- **只配 Qwen 一个 key 就够了**。DeepSeek 是 fallback 备用,只在 Qwen 临时挂掉/超时/限流时才被调用。
- **不配 DeepSeek 完全 OK**。如果你不想再注册一个账号,直接把 `FINANCE_LLM_DEEPSEEK_API_KEY` 这一行删掉或留空。代码会**优雅降级**:Qwen 也失败时,直接显示规则引擎产出的原始建议文本(不带润色,但功能完整、信息无损),不会让"资产体检"页面整个挂掉。
- **不要把两个都不配**。两个都没有的话,资产体检模块还能用,但建议文案会比较"机械"(直接是规则代码生成的模板句),阅读体验略差。
- **不要把 key 提交到 git**。`/etc/finance.env` 不在仓库里,只在你自己服务器上,这是有意为之。**永远不要**把 key 写进 `application.yml` 或代码。

---

## 5. 隐私边界

为保护家庭隐私,本应用对送给 LLM 的内容做了严格的**脱敏白名单**:

### 会送给 LLM 的字段(都是结构化代号 / 数字,不含个人信息)

- 资产**类目代号**(如 `CASH_DEPOSIT`、`STOCK_A`、`BOND_GOV`、`REIT`、`INSURANCE_LIFE`)
- 资产**风险等级**(如 `R1` ~ `R5`)
- **金额数字**(¥150,000 这种纯数字)
- **占比数字**(如 "现金占比 35%")
- **规则代号**(如 `CASH_RATIO_TOO_HIGH`、`EQUITY_OVERWEIGHT`)
- 总资产数字、家庭成员**数量**(数字,不是名字)

### **绝不**送给 LLM 的字段(全部脱敏在本地)

- ❌ **账户名 / 银行名 / 券商名**(如 "招商银行卡尾号 8721"、"老婆的华夏基金账户")
- ❌ **用户名 / 真实姓名 / 微信昵称**
- ❌ **账户备注 / 自定义标签 / 操作记录**
- ❌ **家庭名 / 家庭成员姓名 / 关系标签**(如 "爸爸"、"老婆")
- ❌ **任何身份证号 / 手机号 / 邮箱 / 地址**
- ❌ **历史明细 / 流水**(只送当前快照的聚合占比,不送时间序列)

技术上这个边界由后端的 `LlmPromptBuilder` 强制实现,前端页面不参与;送给 Qwen / DeepSeek 的 prompt 内容你可以在 `journalctl -u finance` 日志(DEBUG 级别)中审计。

---

## 6. 成本估算

### Qwen-Plus 当前定价(2026-05 阿里云百炼)

- **输入**: ¥0.0008 / 千 tokens
- **输出**: ¥0.002 / 千 tokens
- **新人免费额度**: 7000 万 tokens / 90 天(turbo + plus 共享额度)

### 单次调用规模

一次"资产体检"单条建议润色,典型 token 用量:

- 输入(system prompt + 1 条 advice 原文 + 数字保真约束):约 **400 tokens**
- 输出(润色后的单条建议,80-120 字):约 **150 tokens**
- **单次成本**: 400 × 0.0008/1000 + 150 × 0.002/1000 = **¥0.00062**(约 0.06 分)

### 月度 / 年度估算

- 家庭场景使用频率:每月录入/调整资产约 5-10 次,每次触发 1-2 次体检,每次体检最多润色 5 条建议
- **月调用次数**: 约 20-50 次
- **月成本**: 50 × ¥0.00062 ≈ **¥0.031**(三分钱)
- **年成本**: ≈ **¥0.37**
- 7000 万免费 token 大约够跑 **12 万次单条建议润色**,等于免费用很久

### DeepSeek-Chat 定价(仅 fallback 触发时)

- 当前(2026-05)有效价: **¥2 / 百万 tokens 输入**, **¥8 / 百万 tokens 输出**(即 ¥0.002/千 输入, ¥0.008/千 输出)
- 仅在 Qwen 挂掉时触发,家庭场景一年加起来可能就几次,可以忽略不计

**结论:全年总开销 < ¥3**。即使最坏情况(Qwen 免费额度用完 + 频繁触发 fallback + 用户狂刷体检),也撑死不超过 ¥10 / 年。

---

## 附录:故障排查

| 现象 | 可能原因 | 排查命令 |
|---|---|---|
| 资产体检建议是"机械模板句" | 两个 key 都没配 / 都失效 | `sudo journalctl -u finance -g "LLM"` 看是不是都返回了 401/403 |
| 体检页面卡住转圈 > 10s | LLM 超时但没正确降级 | 看日志有无 timeout,确认 `application.yml` 里 `llm.timeout-ms` 是 5000 |
| 启动报 "missing FINANCE_LLM_QWEN_API_KEY" | env 文件没加载 | `systemctl show finance | grep Environment`,确认 EnvironmentFile=/etc/finance.env |
| Qwen 返回 401 InvalidApiKey | key 拷错 / 多了空格 | 重新去百炼控制台创建一个,粘贴时注意首尾空白 |
| Qwen 返回 429 RateLimit | 触发并发限流(罕见) | 等几秒重试,或在控制台看是否欠费 |

---

**检索时间: 2026-05-10**

*价格信息来源:阿里云百炼模型价格官方页(<https://help.aliyun.com/zh/model-studio/model-pricing>)、DeepSeek API Pricing 官方页(<https://api-docs.deepseek.com/quick_start/pricing>)。如发现页面价格已变动,请以厂商官方页为准。*
