package com.family.finance.service.checkup.llm;

import com.family.finance.service.checkup.rule.Advice;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 输出校验 · v0.2 FR-40c · 决策 6
 *
 * 三道防线确保 LLM 不会改数字、不会越界:
 * <ol>
 *   <li>所有出现在原文中的数字(¥金额、百分比、个数月数、星级)必须在润色文中至少出现一次</li>
 *   <li>润色文中不能出现原文没有的「具体数字 + 单位」(防 LLM 编造数字)</li>
 *   <li>长度限制 30-300 中文字符(避免 LLM 偷懒返回单句或废话连篇)</li>
 * </ol>
 *
 * 任意一项失败 → reject,fallback 到 raw 文案。
 */
public final class OutputValidator {

    /** 抓所有数字片段:整数、小数、带单位、含 ¥ / % / pp / 月 / 个 */
    private static final Pattern NUMBER_TOKEN = Pattern.compile(
            "[¥\\$]?\\d+(?:\\.\\d+)?(?:%|pp|个|月|期|星|★|分点)?"
    );

    private OutputValidator() {
    }

    public static Result check(Advice advice, String polished) {
        if (polished == null || polished.isBlank()) return Result.reject("空字符串");
        String trimmed = polished.trim();
        int len = trimmed.length();
        if (len < 30) return Result.reject("文本过短 len=" + len);
        if (len > 300) return Result.reject("文本过长 len=" + len);

        Set<String> originalNumbers = extractNumbers(advice.rawTitle() + " " + advice.rawBody());
        Set<String> polishedNumbers = extractNumbers(trimmed);

        // 防丢失:原文每个数字 token 必须保留
        for (String n : originalNumbers) {
            if (!polishedNumbers.contains(n)) {
                return Result.reject("数字丢失: " + n);
            }
        }
        // 防新增:润色文不能出现原文没有的数字
        for (String n : polishedNumbers) {
            if (!originalNumbers.contains(n)) {
                return Result.reject("新增数字: " + n);
            }
        }

        // 防口语化丢失分类术语(可选,简化版只校验「建议/可考虑/推荐」之类不能出现「您」过多)
        long youCount = trimmed.chars().filter(c -> c == '您').count();
        if (youCount > 3) return Result.reject("过度客套(您 出现 " + youCount + " 次)");

        return Result.accept();
    }

    private static Set<String> extractNumbers(String src) {
        Set<String> tokens = new HashSet<>();
        if (src == null) return tokens;
        Matcher m = NUMBER_TOKEN.matcher(src);
        while (m.find()) {
            tokens.add(normalizeToken(m.group()));
        }
        return tokens;
    }

    /** 规范化:¥1,000 → ¥1000;0.50 → 0.5(去尾零) */
    private static String normalizeToken(String token) {
        String t = token.replace(",", "");
        // 抽数字主体
        Matcher num = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(t);
        if (num.find()) {
            String body = num.group();
            if (body.contains(".")) {
                body = new BigDecimal(body).stripTrailingZeros().toPlainString();
            }
            t = t.substring(0, num.start()) + body + t.substring(num.end());
        }
        return t;
    }

    public record Result(boolean accepted, String reason) {
        public static Result accept() { return new Result(true, null); }
        public static Result reject(String reason) { return new Result(false, reason); }
    }
}
