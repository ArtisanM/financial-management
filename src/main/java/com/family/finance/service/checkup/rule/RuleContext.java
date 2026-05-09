package com.family.finance.service.checkup.rule;

import com.family.finance.service.checkup.AccountDiagnose;
import com.family.finance.service.checkup.FamilyDiagnose;

import java.math.BigDecimal;
import java.util.List;

/**
 * 规则上下文 · 装载所有事实数据(由程序计算),规则只读不改
 *
 * 账户级规则:account 非 null,family 非 null(允许跨账户对比)
 * 家庭级规则:account = null,family + accounts 全集
 *
 * @param avgMonthlyExpense 家庭近 12 月月均支出(本位币),用于流动性月数计算
 */
public record RuleContext(
        AccountDiagnose account,
        FamilyDiagnose family,
        List<AccountDiagnose> accounts,
        BigDecimal avgMonthlyExpense
) {
    public boolean isAccountScope() {
        return account != null;
    }

    public boolean isFamilyScope() {
        return account == null && family != null;
    }

    public static RuleContext forAccount(AccountDiagnose account, FamilyDiagnose family,
                                         List<AccountDiagnose> accounts, BigDecimal avgMonthlyExpense) {
        return new RuleContext(account, family, accounts, avgMonthlyExpense);
    }

    public static RuleContext forFamily(FamilyDiagnose family, List<AccountDiagnose> accounts,
                                        BigDecimal avgMonthlyExpense) {
        return new RuleContext(null, family, accounts, avgMonthlyExpense);
    }
}
