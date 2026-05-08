package com.family.finance.factview;

import com.family.finance.calc.PnlCalculator;
import com.family.finance.domain.account.AccountClass;
import com.family.finance.domain.account.AccountLiquidity;
import com.family.finance.domain.account.AccountType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FactProjector {
    private FactProjector() {
    }

    public static AccountPeriodFact project(FactBaseRow row) {
        AccountType type = AccountType.valueOf(row.accountType());
        BigDecimal fx = row.fxToBase() == null ? BigDecimal.ONE : row.fxToBase();
        BigDecimal incomeOrig = money(nz(row.incomeOrig()));
        BigDecimal expenseOrig = money(nz(row.expenseOrig()));
        BigDecimal transferInOrig = money(nz(row.transferInOrig()));
        BigDecimal transferOutOrig = money(nz(row.transferOutOrig()));
        BigDecimal periodPnlOrig = PnlCalculator.periodPnl(
                row.endBalance(), row.previousEndBalance(), incomeOrig, expenseOrig, transferInOrig, transferOutOrig);
        BigDecimal periodPnlBase = PnlCalculator.toBase(periodPnlOrig, fx);

        return new AccountPeriodFact(
                row.accountId(),
                row.accountName(),
                type,
                classOf(type),
                liquidityOf(type),
                row.accountCurrency(),
                row.ownerId(),
                row.displayOrder(),
                row.periodId(),
                row.periodStart(),
                row.periodEnd(),
                moneyOrNull(row.previousEndBalance()),
                moneyOrNull(row.endBalance()),
                PnlCalculator.toBase(row.previousEndBalance(), fx),
                PnlCalculator.toBase(row.endBalance(), fx),
                incomeOrig,
                PnlCalculator.toBase(incomeOrig, fx),
                expenseOrig,
                PnlCalculator.toBase(expenseOrig, fx),
                transferInOrig,
                PnlCalculator.toBase(transferInOrig, fx),
                transferOutOrig,
                PnlCalculator.toBase(transferOutOrig, fx),
                periodPnlOrig,
                periodPnlBase,
                fx.setScale(6, RoundingMode.HALF_EVEN)
        );
    }

    public static AccountClass classOf(AccountType type) {
        return type == AccountType.LOAN ? AccountClass.LIABILITY : AccountClass.ASSET;
    }

    public static AccountLiquidity liquidityOf(AccountType type) {
        return switch (type) {
            case CASH -> AccountLiquidity.LIQUID;
            case WEALTH, STOCK -> AccountLiquidity.SEMI_LIQUID;
            case PROPERTY -> AccountLiquidity.ILLIQUID;
            case LOAN, OTHER -> AccountLiquidity.NA;
        };
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal moneyOrNull(BigDecimal value) {
        return value == null ? null : money(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }
}
