package com.family.finance.factview;

import com.family.finance.domain.account.AccountClass;
import com.family.finance.domain.account.AccountLiquidity;
import com.family.finance.domain.account.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountPeriodFact(
        Long accountId,
        String accountName,
        AccountType accountType,
        AccountClass accountClass,
        AccountLiquidity accountLiquidity,
        String accountCurrency,
        Long ownerId,
        Integer displayOrder,
        Long periodId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal previousEndBalanceOrig,
        BigDecimal endBalanceOrig,
        BigDecimal previousEndBalanceBase,
        BigDecimal endBalanceBase,
        BigDecimal incomeOrig,
        BigDecimal incomeBase,
        BigDecimal expenseOrig,
        BigDecimal expenseBase,
        BigDecimal transferInOrig,
        BigDecimal transferInBase,
        BigDecimal transferOutOrig,
        BigDecimal transferOutBase,
        BigDecimal periodPnlOrig,
        BigDecimal periodPnlBase,
        BigDecimal fxToBase
) {
    public BigDecimal netExternalBase() {
        return incomeBase.subtract(expenseBase);
    }

    public BigDecimal netExternalOrig() {
        return incomeOrig.subtract(expenseOrig);
    }

    public BigDecimal netTransferOrig() {
        return transferInOrig.subtract(transferOutOrig);
    }
}
