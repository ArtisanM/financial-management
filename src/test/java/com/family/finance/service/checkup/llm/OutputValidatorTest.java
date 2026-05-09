package com.family.finance.service.checkup.llm;

import com.family.finance.service.checkup.rule.Advice;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputValidatorTest {

    private static Advice advice(String title, String body) {
        return Advice.of("RET-3", Advice.Scope.ACCOUNT, 1L,
                Advice.Dimension.RETURN_QUALITY, Advice.Severity.WARN,
                "收益评估", title, body, null);
    }

    @Test
    void rejectsEmpty() {
        var r = OutputValidator.check(advice("跑输基准", "近 12 期年化 10%, 跑输 3pp。"), "");
        assertThat(r.accepted()).isFalse();
    }

    @Test
    void rejectsTooShort() {
        var r = OutputValidator.check(advice("跑输基准", "近 12 期年化 10%, 跑输 3pp。"), "太短了。");
        assertThat(r.accepted()).isFalse();
    }

    @Test
    void rejectsTooLong() {
        String veryLong = "近 12 期年化 10% 跑输 3pp。" + "无关内容".repeat(80);
        var r = OutputValidator.check(advice("跑输基准", "近 12 期年化 10%, 跑输 3pp。"), veryLong);
        assertThat(r.accepted()).isFalse();
        assertThat(r.reason()).contains("过长");
    }

    @Test
    void rejectsLostNumber() {
        var ad = advice("跑输基准", "近 12 期年化 10%, 跑输 3pp。");
        // 润色文丢失 "12" 与 "3pp"
        var r = OutputValidator.check(ad, "近期组合表现不及预期,主动选股偏差较大。建议适度配置指数型产品以贴近基准并平滑短期波动。");
        assertThat(r.accepted()).isFalse();
        assertThat(r.reason()).contains("丢失");
    }

    @Test
    void rejectsAddedNumber() {
        var ad = advice("跑输基准", "近 12 期年化 10%, 跑输 3pp。");
        // 润色文新增了 25%(原文没有)
        var r = OutputValidator.check(ad,
                "近 12 期年化 10%, 组合跑输基准 3pp。建议将 25% 仓位调整至指数型产品,以贴近基准并平滑短期波动。");
        assertThat(r.accepted()).isFalse();
        assertThat(r.reason()).contains("新增数字");
    }

    @Test
    void acceptsValidPolish() {
        var ad = advice("跑输基准", "近 12 期年化 10%, 跑输 3pp。");
        String polish = "近 12 期年化 10%, 组合较类目基准跑输约 3pp。建议复盘持仓集中度,可适度配置指数型产品以贴近基准走势,平滑短期主动选股偏差。";
        var r = OutputValidator.check(ad, polish);
        assertThat(r.accepted()).isTrue();
    }

    @Test
    void rejectsExcessiveFormality() {
        var ad = advice("跑输基准", "近 12 期年化 10%, 跑输 3pp。");
        String polish = "您好。近 12 期年化 10%, 跑输 3pp。请您好好考虑分散方案。请您先评估您的风险承受度,请您再决定。";
        var r = OutputValidator.check(ad, polish);
        assertThat(r.accepted()).isFalse();
        assertThat(r.reason()).contains("客套");
    }

    @Test
    void exactNumberNormalizationWorks() {
        var ad = advice("敞口", "本账户敞口 40%, 超过推荐 30%。");
        // 用全角逗号 / 千分位 不影响数字提取(本测试不验证全角,验证基本 ascii)
        String polish = "本账户敞口 40%, 已超过推荐组合集中度上限 30%。建议将部分仓位再配置至中低风险类目,降低单账户风险敞口。";
        var r = OutputValidator.check(ad, polish);
        assertThat(r.accepted()).isTrue();
    }
}
