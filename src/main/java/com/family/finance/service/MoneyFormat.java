package com.family.finance.service;

import com.family.finance.domain.account.AccountType;

import java.math.BigDecimal;
import java.text.DecimalFormat;

final class MoneyFormat {
    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0");

    private MoneyFormat() {
    }

    static String format(String currency, BigDecimal amount) {
        if (amount == null) {
            return "— 待填";
        }
        String symbol = switch (currency) {
            case "USD" -> "$";
            case "HKD" -> "HK$";
            default -> "¥";
        };
        String sign = amount.signum() < 0 ? "−" : "";
        return sign + symbol + WHOLE.format(amount.abs());
    }

    static String formatForAccount(AccountType type, String currency, BigDecimal amount) {
        if (amount == null) {
            return "— 待填";
        }
        return format(currency, amount);
    }

    static String formatDelta(String currency, BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        if (amount.signum() == 0) {
            return "—";
        }
        return (amount.signum() > 0 ? "+" : "") + format(currency, amount);
    }
}
