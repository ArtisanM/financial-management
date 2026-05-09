# LLM 厂商选型对比 · 资产体检 AI 文案润色

> 调研日期 2026-05-09 · 场景:中文短文本(60-80 字)润色,投顾语调,月调用 < 200 次,中国大陆部署

## TL;DR

**首选 Qwen-Turbo**(阿里百炼):单价最低、OpenAI 协议最完整、国内合规与稳定性最优,月成本 < ¥0.1。备选 DeepSeek-v4-flash 作为 fallback。

## 对比矩阵

| 维度 | DeepSeek | Qwen (百炼) | Kimi (Moonshot) |
|------|----------|-------------|-----------------|
| 主力模型 | deepseek-v4-flash | qwen-turbo / qwen-plus | moonshot-v1-8k / kimi-k2 |
| 输入价 ¥/M | 1.0 (cache miss) / 0.02 (hit) | 0.30 / 0.80 | 2.0 / 4.0 |
| 输出价 ¥/M | 2.0 | 0.60 / 2.00 | 10.0 / 16.0 |
| 100 次调用成本(80K in+20K out) | ¥0.12 | **¥0.036 / ¥0.10** | ¥0.36 / ¥0.64 |
| OpenAI 兼容 | 完整 | 完整(`compatible-mode/v1`) | 完整(`api.moonshot.cn/v1`) |
| streaming / temperature | 是 / 是 | 是 / 是 | 是 / 是 |
| response_format JSON | 是 | 未明确公开 | 是(`json_object` + `json_schema`) |
| function calling | 是 | 是 | 是(最多 128 tools) |
| 限流(付费基础档) | 动态并发,无公开 RPM | qwen-turbo 1200 RPM 级 | T1(¥50): 200 RPM / 2M TPM |
| 数据用于训练 | 默认是,可在 console 关 "Improve the model" | 默认不用于训练(企业版承诺) | 默认不用于训练 |
| 近期破坏性变更 | `deepseek-chat`/`reasoner` 别名 2026-07-24 弃用 | 模型版本日期化,旧版长期保留 | K2 系列 2026-05-25 下线,迁 K2.6 |
| 中文 benchmark | SuperCLUE 2025 H1 开源第一梯队 | C-Eval/CMMLU 长期前列 | SuperCLUE 中上,K2.6 编码 Tier A |
| SLA 公开 | 否 | 阿里云 SLA 体系覆盖 | 否 |

## 详细分析

### DeepSeek
v4-flash 单次调用约 ¥0.0012,极便宜。OpenAI 协议完整。**两个减分项**:(1) 限流是动态的,高峰期 429 概率高,且不能付费提档;(2) 默认会用对话改进模型,需手动在 console/API 关闭 "Improve the model for everyone"。适合做 **fallback**。

### Qwen (阿里百炼)
qwen-turbo 是本场景的成本王者(¥0.036 / 100 次)。OpenAI 兼容端点 `https://dashscope.aliyuncs.com/compatible-mode/v1` 成熟,阿里云 SLA + 备案体系对国内 app 上架友好,新用户有 70M tokens 免费额度。润色质量若 turbo 不够,平滑切到 qwen-plus(¥0.10/100 次)。**唯一注意**:模型名带日期版本,需在配置里固定一个 stable alias(如 `qwen-turbo-latest`)。

### Kimi (Moonshot)
本场景**最贵**:moonshot-v1-8k 已 ¥0.36/100 次,kimi-k2-0905 ¥0.64/100 次。优势在长文本与 JSON Schema 严格输出,但 60-80 字润色用不到。T0 免费档仅 3 RPM,几乎不可用,T1 需充 ¥50。不推荐用于本场景。

## 推荐

1. **主**: `qwen-turbo`,base_url `https://dashscope.aliyuncs.com/compatible-mode/v1`。
2. **质量升级位**:同协议切 `qwen-plus`,代码零改动。
3. **Fallback**: `deepseek-v4-flash`,base_url `https://api.deepseek.com`。两家都是 OpenAI 协议,只需切 `base_url` + `api_key` + `model`,封装成策略模式即可,**切换成本 < 30 行代码**。

## 风险与备选

- **Qwen 模型版本漂移**:固定 `-latest` 别名,关键 prompt 在 CI 中跑回归。
- **DeepSeek 限流**:高峰 429,需指数退避 + 熔断,不要做同步 UI 阻塞调用。
- **数据合规**:三家服务器均在境内,但 DeepSeek 默认训练开关需主动关闭;若处理用户真实金额,prompt 中 mask 数字后再发送。
- **Kimi 保留位**:若未来需要长文档摘要(账单 PDF 解析),再单独引入 kimi-k2.6(262K 上下文)。
