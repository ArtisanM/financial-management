package com.family.finance.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 类目基准对照 · v0.2 资产体检
 *
 * 把账户的「年化收益率」与该类目预设的 benchmark_pct 比较,得出「跑赢/跑输 N 个百分点」。
 *
 * 我们刻意不做指数累计(没有 daily/monthly 基准时间序列数据)。
 * benchmark_pct 是**常数年化**(如 A 股 = 8%),与账户 XIRR 直接做差即可。
 *
 * 决策见 TDD § 决策 4 / FR-40d
 */
public final class BenchmarkComparator {
    private BenchmarkComparator() {
    }

    /**
     * @param accountAnnualizedReturn 账户年化(如 0.105 = 10.5%);null 表示样本不足
     * @param benchmarkAnnualizedPct  类目基准年化(如 8.00 表示 8%);null 表示无稳定基准
     * @return 比较结果;basis 为 NULL 时输出 noBenchmark()
     */
    public static Result compare(BigDecimal accountAnnualizedReturn, BigDecimal benchmarkAnnualizedPct) {
        if (benchmarkAnnualizedPct == null) {
            return Result.noBenchmark();
        }
        if (accountAnnualizedReturn == null) {
            return Result.insufficientSample(benchmarkAnnualizedPct);
        }
        BigDecimal accountPct = accountAnnualizedReturn.multiply(new BigDecimal("100"));
        BigDecimal diff = accountPct.subtract(benchmarkAnnualizedPct).setScale(2, RoundingMode.HALF_EVEN);
        return new Result(
                Status.COMPARED,
                benchmarkAnnualizedPct.setScale(2, RoundingMode.HALF_EVEN),
                accountPct.setScale(2, RoundingMode.HALF_EVEN),
                diff
        );
    }

    public enum Status {
        COMPARED,           // 有基准,有账户数据,做了对照
        NO_BENCHMARK,       // 类目无 benchmark_pct(如现金 / 房产)
        INSUFFICIENT_SAMPLE // 类目有基准但账户样本太少
    }

    public record Result(
            Status status,
            BigDecimal benchmarkPct,
            BigDecimal accountPct,
            /** 单位:百分点(账户 - 基准),正数=跑赢 */
            BigDecimal diffPct
    ) {
        public static Result noBenchmark() {
            return new Result(Status.NO_BENCHMARK, null, null, null);
        }

        public static Result insufficientSample(BigDecimal benchmarkPct) {
            return new Result(Status.INSUFFICIENT_SAMPLE,
                    benchmarkPct == null ? null : benchmarkPct.setScale(2, RoundingMode.HALF_EVEN),
                    null, null);
        }

        /** UI 简短结论:跑赢 X.Xpp / 跑输 X.Xpp / —(无样本)/ —(无基准) */
        public String summary() {
            if (status == Status.NO_BENCHMARK) return "无基准";
            if (status == Status.INSUFFICIENT_SAMPLE) return "样本不足";
            if (diffPct == null) return "—";
            int s = diffPct.signum();
            String absStr = diffPct.abs().toPlainString() + "pp";
            if (s > 0) return "跑赢 " + absStr;
            if (s < 0) return "跑输 " + absStr;
            return "持平";
        }
    }
}
