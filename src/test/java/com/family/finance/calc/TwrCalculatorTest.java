package com.family.finance.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TwrCalculatorTest {

    @Test
    void compoundsMonthlySlices() {
        BigDecimal twr = TwrCalculator.cumulative(List.of(
                point("1000", "1120", "20"),
                point("1120", "1200", "0")
        )).orElseThrow();

        assertThat(twr).isEqualByComparingTo("0.17857143");
    }

    @Test
    void flatNetWorthReturnsZero() {
        BigDecimal twr = TwrCalculator.cumulative(List.of(
                point("1000", "1000", "0"),
                point("1000", "1000", "0")
        )).orElseThrow();

        assertThat(twr).isEqualByComparingTo("0.00000000");
    }

    private static TwrCalculator.TwrPoint point(String start, String end, String netInflow) {
        return new TwrCalculator.TwrPoint(new BigDecimal(start), new BigDecimal(end), new BigDecimal(netInflow));
    }
}
