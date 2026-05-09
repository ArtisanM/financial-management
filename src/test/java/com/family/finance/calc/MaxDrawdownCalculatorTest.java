package com.family.finance.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaxDrawdownCalculatorTest {

    @Test
    void emptyOrSinglePointReturnsNull() {
        assertThat(MaxDrawdownCalculator.calculate(null)).isNull();
        assertThat(MaxDrawdownCalculator.calculate(List.of())).isNull();
        assertThat(MaxDrawdownCalculator.calculate(List.of(p("2025-01-01", "1.0")))).isNull();
    }

    @Test
    void monotonicallyRisingHasZeroDrawdown() {
        var result = MaxDrawdownCalculator.calculate(List.of(
                p("2025-01-01", "1.00"),
                p("2025-02-01", "1.05"),
                p("2025-03-01", "1.10"),
                p("2025-04-01", "1.20")
        ));
        assertThat(result.drawdown()).isEqualByComparingTo("0");
        assertThat(result.peakMonth()).isNull();
    }

    @Test
    void monotonicallyFallingShowsFullDrawdownFromFirstPoint() {
        var result = MaxDrawdownCalculator.calculate(List.of(
                p("2025-01-01", "1.00"),
                p("2025-02-01", "0.90"),
                p("2025-03-01", "0.80"),
                p("2025-04-01", "0.70")
        ));
        assertThat(result.drawdown()).isEqualByComparingTo("-0.30");
        assertThat(result.peakMonth()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(result.troughMonth()).isEqualTo(LocalDate.of(2025, 4, 1));
    }

    @Test
    void vShapedRecoveryStillReportsLowestPoint() {
        // 涨到 1.20,跌到 0.90,反弹回 1.30 — 最大回撤 (0.90-1.20)/1.20 = -25%
        var result = MaxDrawdownCalculator.calculate(List.of(
                p("2025-01-01", "1.00"),
                p("2025-02-01", "1.20"),
                p("2025-03-01", "0.90"),
                p("2025-04-01", "1.30")
        ));
        assertThat(result.drawdown()).isEqualByComparingTo("-0.25");
        assertThat(result.peakMonth()).isEqualTo(LocalDate.of(2025, 2, 1));
        assertThat(result.troughMonth()).isEqualTo(LocalDate.of(2025, 3, 1));
    }

    @Test
    void wShapedTwoTroughsPicksDeeperOne() {
        // 1.00 → 1.20(高1) → 0.95(谷1,-21%) → 1.40(高2) → 0.84(谷2,-40%)
        var result = MaxDrawdownCalculator.calculate(List.of(
                p("2025-01-01", "1.00"),
                p("2025-02-01", "1.20"),
                p("2025-03-01", "0.95"),
                p("2025-04-01", "1.40"),
                p("2025-05-01", "0.84")
        ));
        // (0.84 - 1.40) / 1.40 = -0.40
        assertThat(result.drawdown()).isEqualByComparingTo("-0.40");
        assertThat(result.peakMonth()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(result.troughMonth()).isEqualTo(LocalDate.of(2025, 5, 1));
    }

    @Test
    void flatLineHasZeroDrawdown() {
        var result = MaxDrawdownCalculator.calculate(List.of(
                p("2025-01-01", "1.00"),
                p("2025-02-01", "1.00"),
                p("2025-03-01", "1.00")
        ));
        assertThat(result.drawdown()).isEqualByComparingTo("0");
    }

    @Test
    void nullOrZeroNavSkipped() {
        var result = MaxDrawdownCalculator.calculate(List.of(
                p("2025-01-01", "1.00"),
                p("2025-02-01", null),
                p("2025-03-01", "0.85")
        ));
        // null 被跳过,只在 1.00 → 0.85 算 -15%
        assertThat(result.drawdown()).isEqualByComparingTo("-0.15");
    }

    @Test
    void peakUpdatesAfterRecovery() {
        // 跌到 0.80(高 1.00),回弹到 1.20(新高),不应记录新峰之前的旧回撤为最大
        var result = MaxDrawdownCalculator.calculate(List.of(
                p("2025-01-01", "1.00"),
                p("2025-02-01", "0.80"),  // -20%
                p("2025-03-01", "1.20"),  // 新高
                p("2025-04-01", "1.10")   // 自新高 -8.3%
        ));
        // -20% 比 -8.3% 更深,应保留
        assertThat(result.drawdown()).isEqualByComparingTo("-0.20");
        assertThat(result.peakMonth()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(result.troughMonth()).isEqualTo(LocalDate.of(2025, 2, 1));
    }

    @Test
    void smallDrawdownPrecision() {
        var result = MaxDrawdownCalculator.calculate(List.of(
                p("2025-01-01", "100.0000"),
                p("2025-02-01", "99.5000")
        ));
        // -0.5%
        assertThat(result.drawdown()).isEqualByComparingTo("-0.005");
        assertThat(result.drawdownPct()).isEqualTo("-0.5%");
    }

    @Test
    void hasDrawdownFlag() {
        assertThat(MaxDrawdownCalculator.calculate(List.of(
                p("2025-01-01", "1.0"), p("2025-02-01", "1.2")
        )).hasDrawdown()).isFalse();

        assertThat(MaxDrawdownCalculator.calculate(List.of(
                p("2025-01-01", "1.0"), p("2025-02-01", "0.5")
        )).hasDrawdown()).isTrue();
    }

    @Test
    void drawdownPctFormatsTwoDecimals() {
        var r = MaxDrawdownCalculator.calculate(List.of(
                p("2025-01-01", "1.000000"),
                p("2025-02-01", "0.876543")
        ));
        // -12.3457% → 1 位小数 → -12.3%
        assertThat(r.drawdownPct()).isEqualTo("-12.3%");
    }

    private static MaxDrawdownCalculator.NavPoint p(String date, String nav) {
        return new MaxDrawdownCalculator.NavPoint(
                LocalDate.parse(date),
                nav == null ? null : new BigDecimal(nav));
    }
}
