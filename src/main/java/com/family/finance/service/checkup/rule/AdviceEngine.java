package com.family.finance.service.checkup.rule;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 规则引擎入口 · 注入所有 {@link Rule} 实现 → 按 scope 分发 → 按 severity 排序输出
 *
 * Spring 自动收集 {@code List<Rule>} 类型的 bean。新规则只需添加 {@code @Component} 即注册成功。
 */
@Service
public class AdviceEngine {

    private final List<Rule> allRules;

    public AdviceEngine(List<Rule> allRules) {
        this.allRules = allRules == null ? List.of() : allRules;
    }

    public List<Advice> evaluate(RuleContext ctx) {
        Advice.Scope wantScope = ctx.isAccountScope() ? Advice.Scope.ACCOUNT : Advice.Scope.FAMILY;
        List<Advice> hits = new ArrayList<>();
        for (Rule rule : allRules) {
            if (rule.scope() != wantScope) continue;
            try {
                rule.evaluate(ctx).ifPresent(hits::add);
            } catch (Exception ignored) {
                // 单条规则异常不应阻塞其余规则
            }
        }
        // 严重程度倒序:DANGER > WARN > INFO > OK
        hits.sort(Comparator.comparingInt(a -> -a.severity().ordinal()));
        return hits;
    }
}
