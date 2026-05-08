package com.family.finance.domain.period;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodMemberCompletion {
    private Long id;
    private Long periodId;
    private Long memberId;
    private LocalDateTime completedAt;
}
