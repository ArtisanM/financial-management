package com.family.finance.calc;

import java.math.BigDecimal;

public final class IdentityVerifier {
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private IdentityVerifier() {
    }

    public static IdentityCheck verifyMain(BigDecimal netWorthThis,
                                           BigDecimal netWorthPrev,
                                           BigDecimal externalIncome,
                                           BigDecimal externalExpense,
                                           BigDecimal investmentPnl) {
        BigDecimal lhs = nz(netWorthThis).subtract(nz(netWorthPrev));
        BigDecimal rhs = nz(externalIncome).subtract(nz(externalExpense)).add(nz(investmentPnl));
        BigDecimal diff = lhs.subtract(rhs);
        return new IdentityCheck(lhs, rhs, diff, diff.abs().compareTo(TOLERANCE) <= 0);
    }

    public static void assertMain(BigDecimal netWorthThis,
                                  BigDecimal netWorthPrev,
                                  BigDecimal externalIncome,
                                  BigDecimal externalExpense,
                                  BigDecimal investmentPnl) {
        IdentityCheck check = verifyMain(netWorthThis, netWorthPrev, externalIncome, externalExpense, investmentPnl);
        if (!check.ok()) {
            throw new IllegalStateException(
                    "主恒等式违反: lhs=%s, rhs=%s, diff=%s".formatted(check.lhs(), check.rhs(), check.diff()));
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record IdentityCheck(BigDecimal lhs, BigDecimal rhs, BigDecimal diff, boolean ok) {
    }
}
