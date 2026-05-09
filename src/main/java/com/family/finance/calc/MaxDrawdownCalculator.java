package com.family.finance.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 最大回撤算法 · v0.2 资产体检 · 决策 5
 *
 * 输入「如同基金」NAV 序列(已扣外部流入流出),输出最差时点累计跌幅。
 * NAV 序列的构造由 {@link NavSeriesBuilder} 负责;本类只关心数学。
 *
 * 公式:DD(t) = (NAV(t) − peak(t)) / peak(t),其中 peak(t) = max(NAV[0..t])
 *      maxDrawdown = min(DD(t)) over all t,以 0 到 -1 之间的负数表示。
 */
public final class MaxDrawdownCalculator {
    private MaxDrawdownCalculator() {
    }

    public static Result calculate(List<NavPoint> series) {
        if (series == null || series.size() < 2) return null;
        BigDecimal peak = null;
        LocalDate peakDate = null;
        BigDecimal worstDd = BigDecimal.ZERO;
        LocalDate worstPeakDate = null;
        LocalDate worstTroughDate = null;
        BigDecimal worstPeak = null;
        BigDecimal worstTrough = null;

        for (NavPoint p : series) {
            BigDecimal nav = p.nav();
            if (nav == null || nav.signum() <= 0) continue;
            if (peak == null || nav.compareTo(peak) > 0) {
                peak = nav;
                peakDate = p.month();
            }
            BigDecimal dd = nav.subtract(peak).divide(peak, 8, RoundingMode.HALF_EVEN);
            if (dd.compareTo(worstDd) < 0) {
                worstDd = dd;
                worstPeakDate = peakDate;
                worstTroughDate = p.month();
                worstPeak = peak;
                worstTrough = nav;
            }
        }
        if (worstDd.signum() == 0) {
            return new Result(BigDecimal.ZERO, null, null, null, null);
        }
        return new Result(
                worstDd.setScale(6, RoundingMode.HALF_EVEN),
                worstPeakDate,
                worstTroughDate,
                worstPeak,
                worstTrough);
    }

    public record NavPoint(LocalDate month, BigDecimal nav) {
    }

    /**
     * @param drawdown 负数,例如 -0.18 = -18%。无回撤为 0
     * @param peakMonth 高点所在月,无回撤为 null
     * @param troughMonth 低点所在月,无回撤为 null
     */
    public record Result(
            BigDecimal drawdown,
            LocalDate peakMonth,
            LocalDate troughMonth,
            BigDecimal peakNav,
            BigDecimal troughNav
    ) {
        public boolean hasDrawdown() {
            return drawdown != null && drawdown.signum() < 0;
        }

        public String drawdownPct() {
            if (drawdown == null) return "—";
            BigDecimal pct = drawdown.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_EVEN);
            return pct.toPlainString() + "%";
        }
    }
}
