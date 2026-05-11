package com.family.finance.service;

import com.family.finance.domain.account.AccountType;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public final class MoneyFormat {
    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0");
    private static final DecimalFormat TWO_DP = new DecimalFormat("#,##0.00");

    private MoneyFormat() {
    }

    /** 仅返回币种符号(USD→$ / HKD→HK$ / 其它→¥) — 供 Thymeleaf 在 ¥ 字面之前用 */
    public static String symbol(String currency) {
        if (currency == null) return "¥";
        return switch (currency.toUpperCase()) {
            case "USD" -> "$";
            case "HKD" -> "HK$";
            default -> "¥";
        };
    }

    public static String format(String currency, BigDecimal amount) {
        if (amount == null) {
            return "— 待填";
        }
        String sign = amount.signum() < 0 ? "−" : "";
        return sign + symbol(currency) + WHOLE.format(amount.abs());
    }

    /** 含 2 位小数版,供账户详情页"当前本期余额"等精确显示场景 */
    public static String format2(String currency, BigDecimal amount) {
        if (amount == null) return "— 待填";
        String sign = amount.signum() < 0 ? "−" : "";
        return sign + symbol(currency) + TWO_DP.format(amount.abs());
    }

    public static String formatForAccount(AccountType type, String currency, BigDecimal amount) {
        if (amount == null) {
            return "— 待填";
        }
        return format(currency, amount);
    }

    public static String formatDelta(String currency, BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        if (amount.signum() == 0) {
            return "—";
        }
        return (amount.signum() > 0 ? "+" : "") + format(currency, amount);
    }
}
