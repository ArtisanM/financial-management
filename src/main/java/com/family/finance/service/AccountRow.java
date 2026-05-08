package com.family.finance.service;

import com.family.finance.domain.account.Account;
import com.family.finance.domain.snapshot.PeriodSnapshot;

import java.math.BigDecimal;

public record AccountRow(
        Account account,
        String ownerName,
        String paymentSourceName,
        PeriodSnapshot currentSnapshot,
        BigDecimal currentBalance,
        String balanceLabel,
        boolean archived
) {
}
