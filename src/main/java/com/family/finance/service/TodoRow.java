package com.family.finance.service;

import com.family.finance.domain.account.Account;
import com.family.finance.domain.snapshot.PeriodSnapshot;
import com.family.finance.domain.snapshot.SnapshotTodo;

public record TodoRow(
        SnapshotTodo todo,
        Account account,
        PeriodSnapshot currentSnapshot,
        PeriodSnapshot previousSnapshot,
        String ownerName,
        String currentBalanceLabel,
        String previousBalanceLabel
) {
}
