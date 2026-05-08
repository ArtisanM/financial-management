package com.family.finance.service;

import com.family.finance.domain.account.Account;
import com.family.finance.domain.snapshot.PeriodSnapshot;
import com.family.finance.domain.snapshot.SnapshotTodo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        boolean suggestTransfer,
        /** 本期已收到的来自其他账户的划转明细(用于接收方账户的"已收到"提示) */
        List<TransferRef> incoming,
        /** 本期已划出到其他账户的明细 */
        List<TransferRef> outgoing,
        /** PRD FR-7/8/9:本期所有流水合并视图(snapshot/cash_flow/transfer 统一为 LedgerEntry) */
        List<LedgerEntry> ledger
) {
    public record TransferRef(String counterpartyName, BigDecimal amount, String amountLabel) {}

    /**
     * 流水统一视图。kind 用 SNAPSHOT / INCOME / EXPENSE / TRANSFER_IN / TRANSFER_OUT 区分。
     * amountSignedLabel 已带正负号 + 币种符号(便于模板直显)。
     */
    public record LedgerEntry(
            LedgerKind kind,
            LocalDateTime occurredAt,
            BigDecimal amount,
            String amountSignedLabel,
            /** 收入/支出 → 类别名;划转 → 对方账户名;snapshot → null */
            String label,
            /** 用户备注;snapshot 时为修改人显示名 */
            String note
    ) {}

    public enum LedgerKind {
        /** 本期末余额已写入(snapshot 最新值) */
        SNAPSHOT,
        INCOME,
        EXPENSE,
        TRANSFER_IN,
        TRANSFER_OUT
    }
}
