package com.family.finance.factview;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FactBaseRow(
        Long accountId,
        String accountName,
        String accountType,
        String accountCurrency,
        Long ownerId,
        Integer displayOrder,
        Long periodId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal previousEndBalance,
        BigDecimal endBalance,
        BigDecimal incomeOrig,
        BigDecimal expenseOrig,
        BigDecimal transferInOrig,
        BigDecimal transferOutOrig,
        BigDecimal fxToBase
) {
}
