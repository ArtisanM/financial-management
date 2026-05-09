package com.family.finance.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkComparatorTest {

    @Test
    void noBenchmarkWhenCategoryHasNullBenchmark() {
        var r = BenchmarkComparator.compare(new BigDecimal("0.10"), null);
        assertThat(r.status()).isEqualTo(BenchmarkComparator.Status.NO_BENCHMARK);
        assertThat(r.summary()).isEqualTo("无基准");
    }

    @Test
    void insufficientSampleWhenAccountReturnNull() {
        var r = BenchmarkComparator.compare(null, new BigDecimal("8.00"));
        assertThat(r.status()).isEqualTo(BenchmarkComparator.Status.INSUFFICIENT_SAMPLE);
        assertThat(r.benchmarkPct()).isEqualByComparingTo("8.00");
        assertThat(r.summary()).isEqualTo("样本不足");
    }

    @Test
    void outperformanceShownAsPositiveDiff() {
        // 账户 10.5%,基准 8% → 跑赢 2.50pp
        var r = BenchmarkComparator.compare(new BigDecimal("0.105"), new BigDecimal("8.00"));
        assertThat(r.status()).isEqualTo(BenchmarkComparator.Status.COMPARED);
        assertThat(r.accountPct()).isEqualByComparingTo("10.50");
        assertThat(r.diffPct()).isEqualByComparingTo("2.50");
        assertThat(r.summary()).isEqualTo("跑赢 2.50pp");
    }

    @Test
    void underperformanceShownAsNegativeDiff() {
        var r = BenchmarkComparator.compare(new BigDecimal("0.05"), new BigDecimal("8.00"));
        assertThat(r.diffPct()).isEqualByComparingTo("-3.00");
        assertThat(r.summary()).isEqualTo("跑输 3.00pp");
    }

    @Test
    void exactMatchShownAsLevel() {
        var r = BenchmarkComparator.compare(new BigDecimal("0.08"), new BigDecimal("8.00"));
        assertThat(r.diffPct()).isEqualByComparingTo("0");
        assertThat(r.summary()).isEqualTo("持平");
    }
}
