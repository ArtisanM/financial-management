package com.family.finance.calc;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.exception.NoBracketingException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class XirrCalculator {
    private static final double LOWER = -0.99d;
    private static final double UPPER = 10.0d;
    private static final double TOLERANCE = 1e-7d;
    private static final int MAX_ITERATIONS = 100;

    private XirrCalculator() {
    }

    public static BigDecimal annualizedOrCumulative(List<CashFlowPoint> flows, int periodCount) {
        if (flows == null || flows.size() < 2) {
            return null;
        }
        if (periodCount < 12) {
            return cumulativeReturn(flows).orElse(null);
        }
        return annualizedXirr(flows)
                .stream()
                .mapToObj(v -> BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_EVEN))
                .findFirst()
                .orElse(null);
    }

    public static OptionalDouble annualizedXirr(List<CashFlowPoint> flows) {
        if (flows == null || flows.size() < 2 || !hasBothSigns(flows)) {
            return OptionalDouble.empty();
        }
        List<CashFlowPoint> sorted = flows.stream()
                .sorted(Comparator.comparing(CashFlowPoint::date))
                .toList();
        LocalDate anchor = sorted.getFirst().date();
        UnivariateFunction function = rate -> sorted.stream()
                .mapToDouble(point -> point.amount().doubleValue()
                        / Math.pow(1.0d + rate, ChronoUnit.DAYS.between(anchor, point.date()) / 365.0d))
                .sum();
        try {
            BrentSolver solver = new BrentSolver(TOLERANCE);
            return OptionalDouble.of(solver.solve(MAX_ITERATIONS, function, LOWER, UPPER));
        } catch (NoBracketingException | TooManyEvaluationsException ex) {
            return OptionalDouble.empty();
        }
    }

    public static Optional<BigDecimal> cumulativeReturn(List<CashFlowPoint> flows) {
        BigDecimal invested = flows.stream()
                .map(CashFlowPoint::amount)
                .filter(v -> v.signum() < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returned = flows.stream()
                .map(CashFlowPoint::amount)
                .filter(v -> v.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (invested.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(returned.divide(invested, 10, RoundingMode.HALF_EVEN)
                .subtract(BigDecimal.ONE)
                .setScale(8, RoundingMode.HALF_EVEN));
    }

    private static boolean hasBothSigns(List<CashFlowPoint> flows) {
        boolean positive = flows.stream().anyMatch(f -> f.amount().signum() > 0);
        boolean negative = flows.stream().anyMatch(f -> f.amount().signum() < 0);
        return positive && negative;
    }

    public record CashFlowPoint(LocalDate date, BigDecimal amount) {
    }
}
