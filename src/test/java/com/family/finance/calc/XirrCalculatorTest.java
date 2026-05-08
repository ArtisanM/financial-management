package com.family.finance.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XirrCalculatorTest {

    @Test
    void annualizedTenPercentForSimpleOneYearHold() {
        double xirr = XirrCalculator.annualizedXirr(List.of(
                point("2025-01-01", "-10000"),
                point("2026-01-01", "11000")
        )).orElseThrow();

        assertThat(xirr).isCloseTo(0.10, withinHalfPercent());
    }

    @Test
    void annualizedAroundSixteenPercentWithMidyearContribution() {
        double xirr = XirrCalculator.annualizedXirr(List.of(
                point("2025-01-01", "-10000"),
                point("2025-07-01", "-5000"),
                point("2026-01-01", "17000")
        )).orElseThrow();

        assertThat(xirr).isCloseTo(0.16, withinHalfPercent());
    }

    @Test
    void zeroForFlatHold() {
        double xirr = XirrCalculator.annualizedXirr(List.of(
                point("2025-01-01", "-10000"),
                point("2026-01-01", "10000")
        )).orElseThrow();

        assertThat(xirr).isCloseTo(0.0, withinHalfPercent());
    }

    @Test
    void zeroWhenFullRedemptionAndReinvestmentEndFlat() {
        double xirr = XirrCalculator.annualizedXirr(List.of(
                point("2025-01-01", "-10000"),
                point("2025-04-01", "10000"),
                point("2025-04-01", "-10000"),
                point("2026-01-01", "10000")
        )).orElseThrow();

        assertThat(xirr).isCloseTo(0.0, withinHalfPercent());
    }

    private static XirrCalculator.CashFlowPoint point(String date, String amount) {
        return new XirrCalculator.CashFlowPoint(LocalDate.parse(date), new BigDecimal(amount));
    }

    private static org.assertj.core.data.Offset<Double> withinHalfPercent() {
        return org.assertj.core.data.Offset.offset(0.005);
    }
}
