package com.family.finance.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PnlCalculator {
    private static final int MONEY_SCALE = 2;

    private PnlCalculator() {
    }

    public static BigDecimal periodPnl(BigDecimal endThis,
                                       BigDecimal endPrev,
                                       BigDecimal income,
                                       BigDecimal expense,
                                       BigDecimal transferIn,
                                       BigDecimal transferOut) {
        if (endThis == null || endPrev == null) {
            return null;
        }
        BigDecimal netExternal = nz(income).subtract(nz(expense));
        BigDecimal netTransfer = nz(transferIn).subtract(nz(transferOut));
        return money(endThis.subtract(endPrev).subtract(netExternal).subtract(netTransfer));
    }

    public static BigDecimal toBase(BigDecimal orig, BigDecimal fxToBase) {
        if (orig == null) {
            return null;
        }
        return money(orig.multiply(fxToBase == null ? BigDecimal.ONE : fxToBase));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }
}
