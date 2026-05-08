package com.family.finance.service;

import com.family.finance.domain.account.AccountType;

import java.math.BigDecimal;

public record AccountTypeSummary(
        AccountType type,
        int count,
        BigDecimal amount,
        String amountLabel
) {
}
