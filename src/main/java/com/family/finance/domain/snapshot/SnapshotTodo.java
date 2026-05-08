package com.family.finance.domain.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotTodo {
    private Long id;
    private Long periodId;
    private Long accountId;
    private Long assignedMemberId;
    private TodoStatus status;
    private LocalDateTime doneAt;
    private Long doneByMemberId;
    private BigDecimal prefilledBalance;
    private Long prefilledTransferId;
}
