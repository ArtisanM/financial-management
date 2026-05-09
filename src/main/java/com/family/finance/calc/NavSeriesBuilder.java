package com.family.finance.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * NAV(月度净值)序列构造 · v0.2 资产体检
 *
 * 把账户从「真实余额 + 外部流入流出」转换成「如同基金」的单位净值序列,
 * 让 MaxDrawdown / 基准对比 / 长期年化等指标能像评估基金一样评估这个账户。
 *
 * 公式:NAV[0] = 1.0
 *      NAV[t] = NAV[t-1] × (endBalance[t] / (endBalance[t-1] + 净外部流入[t]))
 *
 * 其中:净外部流入 = INCOME − EXPENSE + transferIn − transferOut(对该账户而言的进出)
 *
 * 当某期 (endBalance[t-1] + 净流入) ≤ 0 或 NaN,该期跳过(NAV 维持上一期)。
 */
public final class NavSeriesBuilder {
    private NavSeriesBuilder() {
    }

    public static List<MaxDrawdownCalculator.NavPoint> build(List<PeriodPoint> sorted) {
        if (sorted == null || sorted.isEmpty()) return List.of();
        List<MaxDrawdownCalculator.NavPoint> out = new ArrayList<>(sorted.size());
        BigDecimal nav = BigDecimal.ONE;
        BigDecimal prevEnd = null;
        boolean started = false;

        for (PeriodPoint p : sorted) {
            if (p.endBalance() == null) continue;
            if (!started) {
                // 第一期作为基准 NAV=1.0,不论余额多大
                nav = BigDecimal.ONE;
                prevEnd = p.endBalance();
                started = true;
                out.add(new MaxDrawdownCalculator.NavPoint(p.month(), nav));
                continue;
            }
            BigDecimal netInflow = nz(p.income()).subtract(nz(p.expense()))
                    .add(nz(p.transferIn())).subtract(nz(p.transferOut()));
            BigDecimal denom = prevEnd.add(netInflow);
            if (denom.signum() <= 0) {
                // 跳过失真期 · 维持上一期 NAV
                prevEnd = p.endBalance();
                out.add(new MaxDrawdownCalculator.NavPoint(p.month(), nav));
                continue;
            }
            BigDecimal ratio = p.endBalance().divide(denom, 10, RoundingMode.HALF_EVEN);
            nav = nav.multiply(ratio).setScale(8, RoundingMode.HALF_EVEN);
            prevEnd = p.endBalance();
            out.add(new MaxDrawdownCalculator.NavPoint(p.month(), nav));
        }
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 一个月的输入快照(以账户币种计)。
     *
     * @param month       期起 (period_start)
     * @param endBalance  期末余额
     * @param income      该期入账(INCOME 合计,正数)
     * @param expense     该期出账(EXPENSE 合计,正数)
     * @param transferIn  转入合计(本账户作为 to_account)
     * @param transferOut 转出合计(本账户作为 from_account)
     */
    public record PeriodPoint(
            LocalDate month,
            BigDecimal endBalance,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal transferIn,
            BigDecimal transferOut
    ) {
    }
}
