package com.family.finance.domain.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowCategory {
    private String code;
    private String displayName;
    private String kind;
    private Integer sortOrder;
}
