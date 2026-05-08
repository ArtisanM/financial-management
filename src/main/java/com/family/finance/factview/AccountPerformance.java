package com.family.finance.factview;

import com.family.finance.domain.account.AccountType;

import java.math.BigDecimal;
import java.util.List;

public record AccountPerformance(
        Long accountId,
        String accountName,
        AccountType accountType,
        String accountCurrency,
        BigDecimal currentValue,
        BigDecimal xirr,
        List<TrendPoint> sparkline
) {
}
