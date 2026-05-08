package com.family.finance.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PnlCalculatorTest {

    @Test
    void subtractsExternalFlowAndTransfers() {
        BigDecimal pnl = PnlCalculator.periodPnl(
                bd("1300"), bd("1000"), bd("500"), bd("100"), bd("200"), bd("50"));

        assertThat(pnl).isEqualByComparingTo("-250.00");
    }

    @Test
    void loanPrincipalRepaymentIsExplainedByTransferIn() {
        BigDecimal pnl = PnlCalculator.periodPnl(
                bd("-992000"), bd("-1000000"), bd("0"), bd("0"), bd("8000"), bd("0"));

        assertThat(pnl).isEqualByComparingTo("0.00");
    }

    @Test
    void nullPreviousBalanceReturnsNullForFirstPeriod() {
        assertThat(PnlCalculator.periodPnl(bd("1000"), null, bd("0"), bd("0"), bd("0"), bd("0")))
                .isNull();
    }

    @Test
    void convertsOriginalMoneyToBaseCurrency() {
        assertThat(PnlCalculator.toBase(bd("100.00"), bd("7.211000")))
                .isEqualByComparingTo("721.10");
    }

    @Test
    void nullFlowInputsAreZero() {
        assertThat(PnlCalculator.periodPnl(bd("1100"), bd("1000"), null, null, null, null))
                .isEqualByComparingTo("100.00");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
