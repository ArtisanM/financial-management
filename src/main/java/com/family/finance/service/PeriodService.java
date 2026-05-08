package com.family.finance.service;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.domain.audit.AuditLogType;
import com.family.finance.domain.family.Family;
import com.family.finance.domain.period.Period;
import com.family.finance.domain.period.PeriodMemberCompletion;
import com.family.finance.domain.period.PeriodStatus;
import com.family.finance.domain.period.PeriodType;
import com.family.finance.repository.MemberMapper;
import com.family.finance.repository.PeriodMemberCompletionMapper;
import com.family.finance.repository.PeriodMapper;
import com.family.finance.repository.SnapshotTodoMapper;
import com.family.finance.service.recompute.MetricsRecomputeJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PeriodService {

    private final PeriodMapper periodMapper;
    private final MemberMapper memberMapper;
    private final SnapshotTodoMapper snapshotTodoMapper;
    private final PeriodMemberCompletionMapper completionMapper;
    private final com.family.finance.repository.PeriodReopenLogMapper periodReopenLogMapper;
    private final AuditLogService auditLogService;
    private final MetricsRecomputeJob metricsRecomputeJob;

    public Optional<Period> findCurrentOpen(long familyId) {
        return periodMapper.findCurrentOpen(familyId);
    }

    public Period requireCurrentOpen(long familyId) {
        return findCurrentOpen(familyId)
                .orElseThrow(() -> new IllegalStateException("当前没有 OPEN 周期"));
    }

    public List<Period> findRange(long familyId, LocalDate from, LocalDate to) {
        return periodMapper.findRange(familyId, from, to);
    }

    public List<Period> findLatest(long familyId, int limit) {
        return periodMapper.findLatest(familyId, limit);
    }

    @Transactional
    public Period openIfAbsent(Family family, LocalDate startDate) {
        return periodMapper.findByNatural(family.getId(), family.getPeriodType(), startDate)
                .orElseGet(() -> {
                    Period period = Period.builder()
                            .familyId(family.getId())
                            .periodType(family.getPeriodType())
                            .periodStart(startDate)
                            .periodEnd(periodEnd(family.getPeriodType(), startDate))
                            .status(PeriodStatus.OPEN)
                            .build();
                    periodMapper.insert(period);
                    auditLogService.record(family.getId(), null, AuditLogType.PERIOD_OPEN,
                            "period", period.getId(), "自动创建周期 " + startDate);
                    return period;
                });
    }

    @Transactional
    public void close(MemberPrincipal me, long periodId) {
        close(periodId, me.getMemberId(), "关闭周期");
    }

    @Transactional
    public void close(long periodId) {
        close(periodId, null, "全员完成自动关闭周期");
    }

    @Transactional
    public void markCompletedByMember(long periodId, long memberId) {
        Period period = periodMapper.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("周期不存在: " + periodId));
        memberMapper.findById(memberId)
                .filter(member -> member.getFamilyId().equals(period.getFamilyId()))
                .orElseThrow(() -> new IllegalArgumentException("成员不属于该家庭"));
        completionMapper.insertIgnore(PeriodMemberCompletion.builder()
                .periodId(periodId)
                .memberId(memberId)
                .build());
        auditLogService.record(period.getFamilyId(), memberId, AuditLogType.SYSTEM,
                "period", periodId, "成员提交本期完成");
        int activeMembers = memberMapper.countActiveByFamily(period.getFamilyId());
        int completedMembers = completionMapper.countByPeriod(periodId);
        int pendingTodos = snapshotTodoMapper.countPendingByPeriod(periodId);
        if (activeMembers > 0 && completedMembers >= activeMembers && pendingTodos == 0) {
            close(periodId, null, "全员完成并自动关闭周期");
        }
    }

    @Transactional
    public void reopen(MemberPrincipal me, long periodId) {
        reopen(periodId, me.getMemberId(), "手动重新打开周期");
    }

    @Transactional
    public void reopen(long periodId, String reason) {
        reopen(periodId, null, reason);
    }

    private void reopen(long periodId, Long actorMemberId, String reason) {
        Period period = periodMapper.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("周期不存在: " + periodId));
        periodMapper.reopen(period.getFamilyId(), periodId);
        completionMapper.deleteByPeriod(periodId);
        String safeReason = reason == null || reason.isBlank() ? "(未填写)" : reason;
        // PRD FR-12 验收:写入 period_reopen_log 专表
        periodReopenLogMapper.insert(periodId, actorMemberId, safeReason);
        auditLogService.record(period.getFamilyId(), actorMemberId, AuditLogType.PERIOD_REOPEN,
                "period", periodId, "重新打开周期: " + safeReason);
    }

    private void close(long periodId, Long actorMemberId, String summary) {
        Period period = periodMapper.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("周期不存在: " + periodId));
        periodMapper.close(period.getFamilyId(), periodId);
        auditLogService.record(period.getFamilyId(), actorMemberId, AuditLogType.PERIOD_CLOSE,
                "period", periodId, summary);
        runMetricsAfterCommit(periodId);
    }

    private void runMetricsAfterCommit(long periodId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    metricsRecomputeJob.run(periodId);
                }
            });
        } else {
            metricsRecomputeJob.run(periodId);
        }
    }

    public boolean isPeriodStartDate(PeriodType type, LocalDate date) {
        return switch (type) {
            case MONTHLY -> date.getDayOfMonth() == 1;
            case WEEKLY -> date.getDayOfWeek() == DayOfWeek.MONDAY;
        };
    }

    public LocalDate currentPeriodStart(PeriodType type, LocalDate today) {
        return switch (type) {
            case MONTHLY -> today.withDayOfMonth(1);
            case WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        };
    }

    private LocalDate periodEnd(PeriodType type, LocalDate startDate) {
        return switch (type) {
            case MONTHLY -> startDate.plusMonths(1).minusDays(1);
            case WEEKLY -> startDate.plusDays(6);
        };
    }
}
