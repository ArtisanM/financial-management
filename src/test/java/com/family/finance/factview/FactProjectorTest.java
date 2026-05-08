package com.family.finance.factview;

import com.family.finance.domain.account.AccountClass;
import com.family.finance.domain.account.AccountLiquidity;
import com.family.finance.domain.account.AccountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FactProjectorTest {

    @Test
    void loanProjectsAsLiabilityWithNoLiquidity() {
        assertThat(FactProjector.classOf(AccountType.LOAN)).isEqualTo(AccountClass.LIABILITY);
        assertThat(FactProjector.liquidityOf(AccountType.LOAN)).isEqualTo(AccountLiquidity.NA);
    }

    @Test
    void cashProjectsAsLiquidAsset() {
        assertThat(FactProjector.classOf(AccountType.CASH)).isEqualTo(AccountClass.ASSET);
        assertThat(FactProjector.liquidityOf(AccountType.CASH)).isEqualTo(AccountLiquidity.LIQUID);
    }

    @Test
    void multipliesOriginalAmountsByFxIntoBaseFields() {
        AccountPeriodFact fact = FactProjector.project(new FactBaseRow(
                1L,
                "富途证券",
                "STOCK",
                "USD",
                1L,
                1,
                10L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                new BigDecimal("1000.00"),
                new BigDecimal("1100.00"),
                new BigDecimal("20.00"),
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                new BigDecimal("0.00"),
                new BigDecimal("7.200000")
        ));

        assertThat(fact.endBalanceBase()).isEqualByComparingTo("7920.00");
        assertThat(fact.incomeBase()).isEqualByComparingTo("144.00");
        assertThat(fact.periodPnlBase()).isEqualByComparingTo("540.00");
    }
}
