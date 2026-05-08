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
public class PeriodSnapshot {
    private Long id;
    private Long periodId;
    private Long accountId;
    private BigDecimal endBalance;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private String note;
}
