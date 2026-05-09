package com.family.finance.service.checkup.llm;

import com.family.finance.service.checkup.rule.Advice;

import java.util.Map;

/**
 * Prompt 构造器 · v0.2 FR-40c
 *
 * 半 PII 隔离原则:
 *   ✅ 可入 prompt:类目 code、风险等级、数字、占比
 *   ❌ 严禁入 prompt:账户名、用户名、备注、家庭名
 *
 * 4 维度 system prompt 模板分别在 {@link DimensionPromptRegistry}。
 */
public final class PromptBuilder {

    private static final Map<Advice.Dimension, String> SYSTEM_PROMPTS = Map.of(
            Advice.Dimension.LIQUIDITY,
                    "你是现代家庭投资顾问助手。语调:专业、克制、可执行。" +
                    "用户给你一条「流动性诊断」原始建议(已有数字与结论),你的任务是用 80-120 字润色文案," +
                    "保持所有数字 100% 不变,不可新增计算或评估,不可加入新建议。语气避免「请」「建议您」等客套," +
                    "直接输出 1 段中文,不带 markdown,不带 emoji。",
            Advice.Dimension.RISK_ALLOCATION,
                    "你是现代家庭投资顾问助手。语调:专业、克制、引用现代组合理论术语(如「风险敞口」「再平衡」)。" +
                    "用户给你一条「风险与配置」原始建议(已有数字),你的任务是用 80-120 字润色," +
                    "保持所有数字 100% 不变,不可新增计算,不可改阈值,不可换示例数。" +
                    "输出 1 段中文,不带 markdown,不带 emoji。",
            Advice.Dimension.RETURN_QUALITY,
                    "你是现代家庭投资顾问助手。语调:专业、克制、引用「跑赢/跑输」「年化」「主动选股偏差」等术语。" +
                    "用户给你一条「收益质量」原始建议,你的任务是用 80-120 字润色," +
                    "保持所有数字 100% 不变,不可新增计算,不可发明基准。" +
                    "输出 1 段中文,不带 markdown,不带 emoji。",
            Advice.Dimension.DEBT_HEALTH,
                    "你是现代家庭投资顾问助手。语调:专业、克制、引用「加速偿还」「现金流」「利息支出」等术语。" +
                    "用户给你一条「负债健康」原始建议,你的任务是用 80-120 字润色," +
                    "保持所有数字 100% 不变,不可新增计算,不可改还款方案。" +
                    "输出 1 段中文,不带 markdown,不带 emoji。"
    );

    private PromptBuilder() {
    }

    public static String systemFor(Advice.Dimension dim) {
        return SYSTEM_PROMPTS.getOrDefault(dim, SYSTEM_PROMPTS.get(Advice.Dimension.RETURN_QUALITY));
    }

    /** 用户消息:把硬事实(原标题 + 原正文)按结构传给 LLM,LLM 不接触账户名/用户名 */
    public static String userFor(Advice advice) {
        StringBuilder sb = new StringBuilder();
        sb.append("规则代号: ").append(advice.ruleId()).append('\n');
        sb.append("严重等级: ").append(advice.severity().name()).append('\n');
        sb.append("维度: ").append(advice.dimension().label).append('\n');
        sb.append("分类: ").append(advice.category()).append('\n');
        sb.append("原标题: ").append(advice.rawTitle()).append('\n');
        sb.append("原正文: ").append(advice.rawBody()).append('\n');
        sb.append("\n请输出润色后的正文(只需 80-120 字中文段,不要重复标题,不要列表,不要 markdown)。");
        return sb.toString();
    }
}
