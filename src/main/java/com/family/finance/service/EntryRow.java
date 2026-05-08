package com.family.finance.service;

import com.family.finance.domain.account.Account;
import com.family.finance.domain.snapshot.PeriodSnapshot;
import com.family.finance.domain.snapshot.SnapshotTodo;

import java.math.BigDecimal;

public record EntryRow(
        Account account,
        String ownerName,
        SnapshotTodo todo,
        PeriodSnapshot currentSnapshot,
        PeriodSnapshot previousSnapshot,
        BigDecimal delta,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal transferIn,
        BigDecimal transferOut,
        BigDecimal unexplained,
        String currentBalanceLabel,
        String previousBalanceLabel,
        String deltaLabel,
        String unexplainedLabel,
        String warning,
        boolean done,
        /** PRD FR-10:|未解释金额| > ¥3000 时给"看起来像账户间转账?"软提示 */
        boolean suggestTransfer
) {
}
