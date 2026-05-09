package com.family.finance.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NavSeriesBuilderTest {

    @Test
    void emptyInputReturnsEmpty() {
        assertThat(NavSeriesBuilder.build(null)).isEmpty();
        assertThat(NavSeriesBuilder.build(List.of())).isEmpty();
    }

    @Test
    void firstPeriodAlwaysHasNav1() {
        var nav = NavSeriesBuilder.build(List.of(
                pp("2025-01-01", "10000", "0", "0", "0", "0")
        ));
        assertThat(nav).hasSize(1);
        assertThat(nav.get(0).nav()).isEqualByComparingTo("1.0");
    }

    @Test
    void pureGrowthWithoutFlowsIsRatio() {
        // 1万→1.1万 NAV=1.10
        var nav = NavSeriesBuilder.build(List.of(
                pp("2025-01-01", "10000", "0", "0", "0", "0"),
                pp("2025-02-01", "11000", "0", "0", "0", "0")
        ));
        assertThat(nav.get(1).nav()).isEqualByComparingTo("1.10");
    }

    @Test
    void inflowDoesNotInflateNav() {
        // 期初 1 万,期内 income 5000(外部流入),期末 1.5 万 → 没有任何收益,NAV=1.0
        var nav = NavSeriesBuilder.build(List.of(
                pp("2025-01-01", "10000", "0", "0", "0", "0"),
                pp("2025-02-01", "15000", "5000", "0", "0", "0")
        ));
        assertThat(nav.get(1).nav()).isEqualByComparingTo("1.0");
    }

    @Test
    void outflowDoesNotDeflateNav() {
        // 1 万 → 期内 expense 2000(外部流出),期末 8000 → 没有亏损
        var nav = NavSeriesBuilder.build(List.of(
                pp("2025-01-01", "10000", "0", "0", "0", "0"),
                pp("2025-02-01", "8000", "0", "2000", "0", "0")
        ));
        assertThat(nav.get(1).nav()).isEqualByComparingTo("1.0");
    }

    @Test
    void transferInDoesNotInflateNav() {
        var nav = NavSeriesBuilder.build(List.of(
                pp("2025-01-01", "10000", "0", "0", "0", "0"),
                pp("2025-02-01", "15000", "0", "0", "5000", "0")
        ));
        assertThat(nav.get(1).nav()).isEqualByComparingTo("1.0");
    }

    @Test
    void mixedFlowsAndTrueGrowthCompose() {
        // 期初 1 万;期内 income 5000 + transferOut 2000 + 真实涨 1000 → 期末 = 1万 + 5000 - 2000 + 1000 = 14000
        // 净流入 = 5000 - 2000 = 3000;denom = 10000 + 3000 = 13000;ratio = 14000/13000 ≈ 1.07692
        var nav = NavSeriesBuilder.build(List.of(
                pp("2025-01-01", "10000", "0", "0", "0", "0"),
                pp("2025-02-01", "14000", "5000", "0", "0", "2000")
        ));
        // NAV = 1 × 14000/13000 ≈ 1.07692308
        assertThat(nav.get(1).nav().doubleValue()).isCloseTo(1.0769231, within(1e-5));
    }

    @Test
    void multiPeriodCompounds() {
        // m1: 10000 → 11000 (NAV=1.10)
        // m2: 11000 → 12100 (NAV=1.21)
        var nav = NavSeriesBuilder.build(List.of(
                pp("2025-01-01", "10000", "0", "0", "0", "0"),
                pp("2025-02-01", "11000", "0", "0", "0", "0"),
                pp("2025-03-01", "12100", "0", "0", "0", "0")
        ));
        assertThat(nav.get(2).nav()).isEqualByComparingTo("1.21");
    }

    @Test
    void zeroDenomFallsBackToPrevNav() {
        // 期初 5000;income 0 expense 0 transfer-out 6000 → denom = 5000 - 6000 = -1000(负数,失真)
        // NAV 维持上一期 = 1.0
        var nav = NavSeriesBuilder.build(List.of(
                pp("2025-01-01", "5000", "0", "0", "0", "0"),
                pp("2025-02-01", "100", "0", "0", "0", "6000")
        ));
        assertThat(nav.get(1).nav()).isEqualByComparingTo("1.0");
    }

    @Test
    void nullEndBalanceSkipped() {
        var nav = NavSeriesBuilder.build(List.of(
                pp("2025-01-01", "10000", "0", "0", "0", "0"),
                ppNull("2025-02-01"),
                pp("2025-03-01", "11000", "0", "0", "0", "0")
        ));
        // 中间一期被跳过,直接从 m1(10000)到 m3(11000)
        assertThat(nav).hasSize(2);
        assertThat(nav.get(1).nav()).isEqualByComparingTo("1.10");
    }

    private static NavSeriesBuilder.PeriodPoint pp(String date, String endBalance,
                                                   String income, String expense,
                                                   String transferIn, String transferOut) {
        return new NavSeriesBuilder.PeriodPoint(
                LocalDate.parse(date),
                new BigDecimal(endBalance),
                new BigDecimal(income),
                new BigDecimal(expense),
                new BigDecimal(transferIn),
                new BigDecimal(transferOut));
    }

    private static NavSeriesBuilder.PeriodPoint ppNull(String date) {
        return new NavSeriesBuilder.PeriodPoint(LocalDate.parse(date), null, null, null, null, null);
    }

    private static org.assertj.core.data.Offset<Double> within(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }
}
