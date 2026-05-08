package com.family.finance.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityVerifierTest {

    @Test
    void passesInsideTolerance() {
        IdentityVerifier.IdentityCheck check = IdentityVerifier.verifyMain(
                bd("1100.00"), bd("1000.00"), bd("150.00"), bd("40.00"), bd("-10.005"));

        assertThat(check.ok()).isTrue();
    }

    @Test
    void throwsOutsideTolerance() {
        assertThatThrownBy(() -> IdentityVerifier.assertMain(
                bd("1100.00"), bd("1000.00"), bd("150.00"), bd("40.00"), bd("-20.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("主恒等式违反");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
