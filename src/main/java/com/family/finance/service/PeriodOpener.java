package com.family.finance.service;

import com.family.finance.domain.account.Account;
import com.family.finance.domain.account.AccountType;
import com.family.finance.domain.family.Family;
import com.family.finance.domain.member.Member;
import com.family.finance.domain.period.Period;
import com.family.finance.domain.snapshot.PeriodSnapshot;
import com.family.finance.domain.snapshot.SnapshotTodo;
import com.family.finance.domain.snapshot.TodoStatus;
import com.family.finance.domain.transfer.Transfer;
import com.family.finance.repository.AccountMapper;
import com.family.finance.repository.MemberMapper;
import com.family.finance.repository.SnapshotMapper;
import com.family.finance.repository.SnapshotTodoMapper;
import com.family.finance.repository.TransferMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PeriodOpener {

    private final FamilyService familyService;
    private final PeriodService periodService;
    private final AccountMapper accountMapper;
    private final MemberMapper memberMapper;
    private final SnapshotMapper snapshotMapper;
    private final SnapshotTodoMapper snapshotTodoMapper;
    private final TransferMapper transferMapper;

    @Scheduled(cron = "0 30 0 * * *")
    @Transactional
    public void openIfDue() {
        LocalDate today = LocalDate.now();
        for (Family family : familyService.findAll()) {
            if (periodService.isPeriodStartDate(family.getPeriodType(), today)) {
                createPeriodAndTodos(family, today);
            }
        }
    }

    @Transactional
    public void createPeriodAndTodos(Family family, LocalDate periodStart) {
        Period period = periodService.openIfAbsent(family, periodStart);
        List<Account> accounts = accountMapper.findActiveByFamily(family.getId());
        Long systemMemberId = memberMapper.findActiveByFamily(family.getId()).stream()
                .findFirst()
                .map(Member::getId)
                .orElse(null);

        for (Account account : accounts) {
            if (snapshotTodoMapper.findByPeriodAndAccount(period.getId(), account.getId()).isPresent()) {
                continue;
            }
            SnapshotTodo todo = SnapshotTodo.builder()
                    .periodId(period.getId())
                    .accountId(account.getId())
                    .assignedMemberId(account.getPrimaryOwnerMemberId())
                    .status(TodoStatus.PENDING)
                    .build();

            if (account.getType() == AccountType.LOAN) {
                applyLoanPrefill(period, account, todo, systemMemberId);
            }
            snapshotTodoMapper.insert(todo);
        }
    }

    private void applyLoanPrefill(Period period, Account loan, SnapshotTodo todo, Long systemMemberId) {
        List<PeriodSnapshot> previous = snapshotMapper.findLatestBefore(loan.getId(), period.getPeriodStart(), 2);
        if (previous.isEmpty()) {
            return;
        }
        BigDecimal prev = previous.get(0).getEndBalance();
        BigDecimal predicted = prev;
        BigDecimal deltaAbs = BigDecimal.ZERO;
        if (previous.size() >= 2) {
            BigDecimal prevPrev = previous.get(1).getEndBalance();
            BigDecimal delta = prev.subtract(prevPrev);
            predicted = prev.add(delta);
            deltaAbs = delta.abs();
        }
        todo.setPrefilledBalance(predicted);

        if (systemMemberId != null
                && loan.getDefaultPaymentSourceAccountId() != null
                && deltaAbs.signum() > 0) {
            Transfer draft = Transfer.builder()
                    .periodId(period.getId())
                    .fromAccountId(loan.getDefaultPaymentSourceAccountId())
                    .toAccountId(loan.getId())
                    .amount(deltaAbs)
                    .occurredAt(period.getPeriodEnd())
                    .note("系统根据上期贷款变化预填")
                    .submittedBy(systemMemberId)
                    .draft(true)
                    .build();
            transferMapper.insert(draft);
            todo.setPrefilledTransferId(draft.getId());
        }
    }
}
