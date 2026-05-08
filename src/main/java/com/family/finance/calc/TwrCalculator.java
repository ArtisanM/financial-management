package com.family.finance.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

public final class TwrCalculator {
    private TwrCalculator() {
    }

    public static Optional<BigDecimal> cumulative(List<TwrPoint> series) {
        BigDecimal product = BigDecimal.ONE;
        boolean any = false;
        for (TwrPoint point : series) {
            if (point.startValue() == null || point.endValue() == null || point.startValue().signum() <= 0) {
                continue;
            }
            BigDecimal numerator = point.endValue()
                    .subtract(nz(point.netInflow()))
                    .subtract(point.startValue());
            BigDecimal periodReturn = numerator.divide(point.startValue(), 10, RoundingMode.HALF_EVEN);
            product = product.multiply(BigDecimal.ONE.add(periodReturn));
            any = true;
        }
        if (!any) {
            return Optional.empty();
        }
        return Optional.of(product.subtract(BigDecimal.ONE).setScale(8, RoundingMode.HALF_EVEN));
    }

    public static Optional<BigDecimal> annualized(BigDecimal cumulativeReturn, int monthsCount) {
        if (cumulativeReturn == null || monthsCount < 12) {
            return Optional.empty();
        }
        double annualized = Math.pow(1.0d + cumulativeReturn.doubleValue(), 12.0d / monthsCount) - 1.0d;
        return Optional.of(BigDecimal.valueOf(annualized).setScale(8, RoundingMode.HALF_EVEN));
    }

    public static BigDecimal annualizedOrCumulative(List<TwrPoint> series, int monthsCount) {
        return cumulative(series)
                .map(value -> annualized(value, monthsCount).orElse(value))
                .orElse(null);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record TwrPoint(BigDecimal startValue, BigDecimal endValue, BigDecimal netInflow) {
    }
}
