package com.family.finance.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ReconciliationCalculator {
    private ReconciliationCalculator() {
    }

    public static BigDecimal unexplained(BigDecimal newBalance,
                                         BigDecimal previousBalance,
                                         BigDecimal income,
                                         BigDecimal expense,
                                         BigDecimal transferIn,
                                         BigDecimal transferOut) {
        if (newBalance == null || previousBalance == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }
        BigDecimal incomeMinusExpense = nz(income).subtract(nz(expense));
        BigDecimal transfers = nz(transferIn).subtract(nz(transferOut));
        return newBalance
                .subtract(previousBalance)
                .subtract(incomeMinusExpense)
                .subtract(transfers)
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
