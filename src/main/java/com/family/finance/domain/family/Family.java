package com.family.finance.domain.family;

import com.family.finance.domain.period.PeriodType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Family {
    private Long id;
    private String name;
    private String brandText;
    private String logoPath;
    private String baseCurrency;
    private PeriodType periodType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
