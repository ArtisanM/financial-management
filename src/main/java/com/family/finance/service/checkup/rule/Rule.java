package com.family.finance.service.checkup.rule;

import java.util.Optional;

/**
 * 规则接口 · v0.2 FR-40c
 *
 * 每条规则:
 * - 规则只读 ctx,纯函数 evaluate
 * - 命中返回 Optional.of(advice),未命中返回 Optional.empty()
 * - 数字 / 阈值 / 文案 全部由规则程序写死(LLM 后续润色文案,但不能改数字)
 */
public interface Rule {
    /** 规则代号,如 LIQ-1, FAM-CON-2 · 用于 audit + UI 显示 */
    String id();

    /** 适用范围 · ACCOUNT 或 FAMILY */
    Advice.Scope scope();

    /** 评估;不命中返回 empty */
    Optional<Advice> evaluate(RuleContext ctx);
}
