package com.family.finance.domain.period;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Period {
    private Long id;
    private Long familyId;
    private PeriodType periodType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private PeriodStatus status;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
}
