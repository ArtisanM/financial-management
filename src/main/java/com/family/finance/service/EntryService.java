package com.family.finance.service;

import com.family.finance.calc.ReconciliationCalculator;
import com.family.finance.domain.account.Account;
import com.family.finance.domain.account.AccountType;
import com.family.finance.domain.audit.AuditLogType;
import com.family.finance.domain.flow.CashFlow;
import com.family.finance.domain.flow.CashFlowKind;
import com.family.finance.domain.member.Member;
import com.family.finance.domain.period.Period;
import com.family.finance.domain.period.PeriodStatus;
import com.family.finance.domain.snapshot.PeriodSnapshot;
import com.family.finance.domain.snapshot.SnapshotTodo;
import com.family.finance.domain.snapshot.TodoStatus;
import com.family.finance.domain.transfer.Transfer;
import com.family.finance.repository.AccountMapper;
import com.family.finance.repository.CashFlowMapper;
import com.family.finance.repository.MemberMapper;
import com.family.finance.repository.PeriodMapper;
import com.family.finance.repository.SnapshotMapper;
import com.family.finance.repository.SnapshotTodoMapper;
import com.family.finance.repository.TransferMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntryService {

    private final AccountMapper accountMapper;
    private final MemberMapper memberMapper;
    private final PeriodMapper periodMapper;
    private final SnapshotMapper snapshotMapper;
    private final SnapshotTodoMapper snapshotTodoMapper;
    private final CashFlowMapper cashFlowMapper;
    private final TransferMapper transferMapper;
    private final AuditLogService auditLogService;

    public Optional<Period> findSelectedPeriod(long familyId, String periodParam) {
        if (periodParam == null || periodParam.isBlank()) {
            return periodMapper.findCurrentOpen(familyId)
                    .or(() -> periodMapper.findLatest(familyId, 1).stream().findFirst());
        }
        if (periodParam.matches("\\d+")) {
            return periodMapper.findById(Long.parseLong(periodParam));
        }
        if (periodParam.matches("\\d{4}-\\d{2}")) {
            int year = Integer.parseInt(periodParam.substring(0, 4));
            int month = Integer.parseInt(periodParam.substring(5, 7));
            return periodMapper.findLatest(familyId, 36).stream()
                    .filter(p -> p.getPeriodStart().getYear() == year && p.getPeriodStart().getMonthValue() == month)
                    .findFirst();
        }
        return Optional.empty();
    }

    public List<EntryRow> listRows(long familyId, long memberId, Period period, boolean mineOnly) {
        List<Account> accounts = accountMapper.findActiveByFamily(familyId).stream()
                .filter(a -> !mineOnly
                        || a.getPrimaryOwnerMemberId() == null
                        || a.getPrimaryOwnerMemberId() == memberId)
                .toList();
        Map<Long, Member> members = memberMapper.findActiveByFamily(familyId).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<Long, PeriodSnapshot> current = snapshotMapper.findByPeriod(period.getId()).stream()
                .collect(Collectors.toMap(PeriodSnapshot::getAccountId, Function.identity()));
        Map<Long, SnapshotTodo> todos = snapshotTodoMapper.findByPeriod(period.getId()).stream()
                .collect(Collectors.toMap(SnapshotTodo::getAccountId, Function.identity()));

        return accounts.stream()
                .map(account -> toRow(account, members, current.get(account.getId()), todos.get(account.getId()), period))
                .sorted(Comparator.comparingInt(r -> Optional.ofNullable(r.account().getDisplayOrder()).orElse(0)))
                .toList();
    }

    public EntryRow rowFor(long familyId, long memberId, long periodId, long accountId) {
        Period period = periodMapper.findById(periodId)
                .filter(p -> p.getFamilyId() == familyId)
                .orElseThrow(() -> new IllegalArgumentException("周期不存在: " + periodId));
        Account account = requireAccount(familyId, accountId);
        Map<Long, Member> members = memberMapper.findActiveByFamily(familyId).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        PeriodSnapshot current = snapshotMapper.findByPeriodAndAccount(periodId, accountId).orElse(null);
        SnapshotTodo todo = snapshotTodoMapper.findByPeriodAndAccount(periodId, accountId).orElse(null);
        return toRow(account, members, current, todo, period);
    }

    @Transactional
    public EntryRow submitBalance(long familyId,
                                  long memberId,
                                  long periodId,
                                  long accountId,
                                  BigDecimal newBalance,
                                  List<CashFlowLine> cashFlowLines,
                                  List<TransferLine> transferLines,
                                  String note) {
        Period period = requireOpenPeriod(familyId, periodId);
        Account account = requireAccount(familyId, accountId);
        BigDecimal normalizedBalance = normalizeBalance(account, newBalance);
        boolean overwriting = snapshotMapper.findByPeriodAndAccount(periodId, accountId).isPresent();

        snapshotMapper.upsert(PeriodSnapshot.builder()
                .periodId(periodId)
                .accountId(accountId)
                .endBalance(normalizedBalance)
                .submittedBy(memberId)
                .note(blankToNull(note))
                .build());

        for (CashFlowLine line : cashFlowLines == null ? List.<CashFlowLine>of() : cashFlowLines) {
            insertCashFlow(period, account, memberId, line);
        }
        for (TransferLine line : transferLines == null ? List.<TransferLine>of() : transferLines) {
            insertTransfer(period, familyId, accountId, line.toAccountId(), line.amount(), line.note(), memberId, false);
        }

        adjustLoanDraft(period, account, normalizedBalance, memberId);
        snapshotTodoMapper.markDone(periodId, accountId, memberId);

        auditLogService.record(familyId, memberId, AuditLogType.SYSTEM, "period_snapshot", accountId,
                overwriting ? "覆盖余额快照" : "提交余额快照");
        EntryRow row = rowFor(familyId, memberId, periodId, accountId);
        if ((account.getType() == AccountType.CASH || account.getType() == AccountType.LOAN)
                && row.unexplained() != null
                && row.unexplained().compareTo(new BigDecimal("0.00")) != 0) {
            auditLogService.record(familyId, memberId, AuditLogType.SYSTEM, "period_snapshot", accountId,
                    "余额轧差未解释: " + row.unexplainedLabel());
        }
        return row;
    }

    @Transactional
    public EntryRow addCashFlow(long familyId,
                                long memberId,
                                long periodId,
                                long accountId,
                                CashFlowKind kind,
                                String categoryCode,
                                BigDecimal amount,
                                String note) {
        Period period = requireOpenPeriod(familyId, periodId);
        Account account = requireAccount(familyId, accountId);
        insertCashFlow(period, account, memberId, new CashFlowLine(kind, categoryCode, amount, note));
        auditLogService.record(familyId, memberId, AuditLogType.SYSTEM, "cash_flow", accountId,
                "新增现金流 " + kind + " " + money(amount));
        return rowFor(familyId, memberId, periodId, accountId);
    }

    @Transactional
    public EntryRow addTransfer(long familyId,
                                long memberId,
                                long periodId,
                                long fromAccountId,
                                long toAccountId,
                                BigDecimal amount,
                                String note,
                                boolean confirmDuplicate) {
        Period period = requireOpenPeriod(familyId, periodId);
        requireAccount(familyId, fromAccountId);
        insertTransfer(period, familyId, fromAccountId, toAccountId, amount, note, memberId, confirmDuplicate);
        auditLogService.record(familyId, memberId, AuditLogType.TRANSFER_CREATE, "transfer", fromAccountId,
                "新增转账 " + fromAccountId + " → " + toAccountId + " " + money(amount));
        return rowFor(familyId, memberId, periodId, fromAccountId);
    }

    @Transactional
    public EntryRow quickTransfer(long familyId,
                                  long memberId,
                                  long fromAccountId,
                                  Long periodId,
                                  long toAccountId,
                                  BigDecimal amount,
                                  String note,
                                  boolean confirmDuplicate) {
        Period period = periodId == null
                ? periodMapper.findCurrentOpen(familyId).orElseThrow(() -> new IllegalStateException("当前没有 OPEN 周期"))
                : requireOpenPeriod(familyId, periodId);
        return addTransfer(familyId, memberId, period.getId(), fromAccountId, toAccountId, amount, note, confirmDuplicate);
    }

    private EntryRow toRow(Account account,
                           Map<Long, Member> members,
                           PeriodSnapshot current,
                           SnapshotTodo todo,
                           Period period) {
        PeriodSnapshot previous = snapshotMapper.findLatestBefore(account.getId(), period.getPeriodStart(), 1)
                .stream()
                .findFirst()
                .orElse(null);
        ReconciliationTotals totals = reconciliationTotals(period.getId(), account.getId());
        BigDecimal currentBalance = current == null ? null : current.getEndBalance();
        BigDecimal previousBalance = previous == null ? null : previous.getEndBalance();
        BigDecimal effectiveBalance = currentBalance != null
                ? currentBalance
                : (todo == null ? null : todo.getPrefilledBalance());
        BigDecimal delta = currentBalance != null && previousBalance != null
                ? currentBalance.subtract(previousBalance)
                : null;
        BigDecimal unexplained = effectiveBalance == null || previousBalance == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN)
                : ReconciliationCalculator.unexplained(
                        effectiveBalance,
                        previousBalance,
                        totals.income(),
                        totals.expense(),
                        totals.transferIn(),
                        totals.transferOut());
        Member owner = account.getPrimaryOwnerMemberId() == null ? null : members.get(account.getPrimaryOwnerMemberId());
        boolean done = current != null || (todo != null && todo.getStatus() == TodoStatus.DONE);
        String currentLabel = currentBalance == null && todo != null && todo.getPrefilledBalance() != null
                ? "预填 " + MoneyFormat.format(account.getCurrency(), todo.getPrefilledBalance())
                : MoneyFormat.format(account.getCurrency(), currentBalance);
        String warning = (account.getType() == AccountType.CASH || account.getType() == AccountType.LOAN)
                && unexplained.signum() != 0
                ? "CASH/LOAN 出现未解释变化,建议补收入、支出或转账"
                : null;
        return new EntryRow(
                account,
                owner == null ? "共同" : owner.getDisplayName(),
                todo,
                current,
                previous,
                delta,
                totals.income(),
                totals.expense(),
                totals.transferIn(),
                totals.transferOut(),
                unexplained,
                currentLabel,
                MoneyFormat.format(account.getCurrency(), previousBalance),
                MoneyFormat.formatDelta(account.getCurrency(), delta),
                MoneyFormat.formatDelta(account.getCurrency(), unexplained),
                warning,
                done,
                // PRD FR-10 智能转账推断:|未解释| > ¥3000 阈值
                unexplained.abs().compareTo(new BigDecimal("3000")) > 0
                && account.getType() != AccountType.LOAN
        );
    }

    private void insertCashFlow(Period period, Account account, long memberId, CashFlowLine line) {
        if (line == null || line.amount() == null || line.amount().signum() == 0) {
            return;
        }
        if (line.kind() == null) {
            throw new IllegalArgumentException("现金流类型必填");
        }
        if (line.categoryCode() == null || line.categoryCode().isBlank()) {
            throw new IllegalArgumentException("现金流类别必填");
        }
        BigDecimal amount = positiveMoney(line.amount());
        cashFlowMapper.insert(CashFlow.builder()
                .periodId(period.getId())
                .accountId(account.getId())
                .kind(line.kind())
                .categoryCode(line.categoryCode())
                .amount(amount)
                .occurredAt(period.getPeriodEnd())
                .note(blankToNull(line.note()))
                .submittedBy(memberId)
                .build());
    }

    private Transfer insertTransfer(Period period,
                                    long familyId,
                                    long fromAccountId,
                                    long toAccountId,
                                    BigDecimal amount,
                                    String note,
                                    long memberId,
                                    boolean confirmDuplicate) {
        if (fromAccountId == toAccountId) {
            throw new IllegalArgumentException("转出/转入账户不能相同");
        }
        requireAccount(familyId, fromAccountId);
        requireAccount(familyId, toAccountId);
        BigDecimal normalized = positiveMoney(amount);
        int duplicate = transferMapper.countRecentDuplicate(period.getId(), fromAccountId, toAccountId, normalized);
        if (duplicate > 0 && !confirmDuplicate) {
            throw new IllegalArgumentException("看起来像 24 小时内重复转账,请确认后再提交");
        }
        Transfer transfer = Transfer.builder()
                .periodId(period.getId())
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(normalized)
                .occurredAt(period.getPeriodEnd())
                .note(blankToNull(note))
                .submittedBy(memberId)
                .draft(false)
                .build();
        transferMapper.insert(transfer);
        return transfer;
    }

    private void adjustLoanDraft(Period period, Account loan, BigDecimal newBalance, long memberId) {
        if (loan.getType() != AccountType.LOAN || loan.getDefaultPaymentSourceAccountId() == null) {
            return;
        }
        PeriodSnapshot previous = snapshotMapper.findLatestBefore(loan.getId(), period.getPeriodStart(), 1)
                .stream()
                .findFirst()
                .orElse(null);
        if (previous == null) {
            return;
        }
        BigDecimal amount = newBalance.subtract(previous.getEndBalance()).abs().setScale(2, RoundingMode.HALF_EVEN);
        if (amount.signum() == 0) {
            return;
        }
        SnapshotTodo todo = snapshotTodoMapper.findByPeriodAndAccount(period.getId(), loan.getId()).orElse(null);
        if (todo != null && todo.getPrefilledTransferId() != null) {
            Transfer draft = transferMapper.findById(todo.getPrefilledTransferId()).orElse(null);
            if (draft != null) {
                draft.setAmount(amount);
                draft.setOccurredAt(period.getPeriodEnd());
                draft.setNote("根据本期贷款余额自动调整并确认");
                draft.setSubmittedBy(memberId);
                draft.setDraft(false);
                transferMapper.updateAmountAndDraft(draft);
                return;
            }
        }
        insertTransfer(period, loan.getFamilyId(), loan.getDefaultPaymentSourceAccountId(), loan.getId(),
                amount, "根据贷款余额变化自动登记本金还款", memberId, true);
    }

    private ReconciliationTotals reconciliationTotals(long periodId, long accountId) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (CashFlow cashFlow : cashFlowMapper.findByPeriodAndAccount(periodId, accountId)) {
            if (cashFlow.getKind() == CashFlowKind.INCOME) {
                income = income.add(cashFlow.getAmount());
            } else {
                expense = expense.add(cashFlow.getAmount());
            }
        }
        BigDecimal transferIn = BigDecimal.ZERO;
        BigDecimal transferOut = BigDecimal.ZERO;
        for (Transfer transfer : transferMapper.findCommittedByPeriodAndAccount(periodId, accountId)) {
            if (transfer.getToAccountId().equals(accountId)) {
                transferIn = transferIn.add(transfer.getAmount());
            }
            if (transfer.getFromAccountId().equals(accountId)) {
                transferOut = transferOut.add(transfer.getAmount());
            }
        }
        return new ReconciliationTotals(
                income.setScale(2, RoundingMode.HALF_EVEN),
                expense.setScale(2, RoundingMode.HALF_EVEN),
                transferIn.setScale(2, RoundingMode.HALF_EVEN),
                transferOut.setScale(2, RoundingMode.HALF_EVEN)
        );
    }

    private Period requireOpenPeriod(long familyId, long periodId) {
        Period period = periodMapper.findById(periodId)
                .filter(p -> p.getFamilyId() == familyId)
                .orElseThrow(() -> new IllegalArgumentException("周期不存在: " + periodId));
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new IllegalStateException("周期已关闭,请先重开再修改");
        }
        return period;
    }

    private Account requireAccount(long familyId, long accountId) {
        return accountMapper.findById(accountId)
                .filter(account -> account.getFamilyId() == familyId)
                .orElseThrow(() -> new IllegalArgumentException("账户不存在: " + accountId));
    }

    private BigDecimal normalizeBalance(Account account, BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("余额必填");
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_EVEN);
        if (account.getType() == AccountType.LOAN && scaled.signum() > 0) {
            return scaled.negate();
        }
        return scaled;
    }

    private BigDecimal positiveMoney(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("金额必填");
        }
        BigDecimal amount = value.abs().setScale(2, RoundingMode.HALF_EVEN);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("金额必须大于 0");
        }
        return amount;
    }

    private String money(BigDecimal amount) {
        return amount == null ? "—" : amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record CashFlowLine(CashFlowKind kind, String categoryCode, BigDecimal amount, String note) {
    }

    public record TransferLine(Long toAccountId, BigDecimal amount, String note) {
    }

    private record ReconciliationTotals(BigDecimal income, BigDecimal expense, BigDecimal transferIn, BigDecimal transferOut) {
    }
}
