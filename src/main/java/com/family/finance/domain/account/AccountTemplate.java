package com.family.finance.domain.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountTemplate {
    private Long id;
    private String code;
    private String displayName;
    private AccountType type;
    private String defaultCurrency;
    private String icon;
    private Integer sortOrder;
    private boolean customSlot;
}
