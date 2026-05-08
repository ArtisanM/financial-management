package com.family.finance.domain.fx;

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
public class FxRate {
    private Long id;
    private Long familyId;
    private String baseCurrency;
    private String quoteCurrency;
    private Long periodId;
    private BigDecimal rate;
    private String source;
    private LocalDateTime fetchedAt;
}
