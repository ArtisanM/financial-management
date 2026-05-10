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
    /** v0.2 FR-1/FR-34:预设图标 code(icon1..icon4),默认 icon2。驱动 iOS apple-touch-icon + PWA manifest;web favicon/nav 在 logo_path 为空时用 */
    private String logoPreset;
    private String baseCurrency;
    private PeriodType periodType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
