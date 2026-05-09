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

    /**
     * 抓数字裸值:整数或小数。**不抓单位后缀**(避免 "12 期" vs "12期" 因空格被判不同 token)。
     * 单位由 LLM 自由改写,验证只关心:原文出现过的数字必须保留;润色文不出现新数字。
     */
    private static final Pattern NUMBER_TOKEN = Pattern.compile(
            "\\d+(?:\\.\\d+)?"
    );

    private OutputValidator() {
    }

    public static Result check(Advice advice, String polished) {
        if (polished == null || polished.isBlank()) return Result.reject("空字符串");
        String trimmed = polished.trim();
        int len = trimmed.length();
        if (len < 30) return Result.reject("文本过短 len=" + len);
        if (len > 300) return Result.reject("文本过长 len=" + len);

        // 抽两类:
        //   strictNumbers: 数字后紧跟单位(%/pp/月/个/期/¥/$/.0+)→ 必须逐字符保留
        //   plainNumbers: 裸数字(后无单位)→ 允许 LLM 改写为中文数字
        Set<String> origStrict = extractStrictNumbers(advice.rawTitle() + " " + advice.rawBody());
        Set<String> polishStrict = extractStrictNumbers(trimmed);
        Set<String> origAll = extractNumbers(advice.rawTitle() + " " + advice.rawBody());
        Set<String> polishAll = extractNumbers(trimmed);

        // 防丢失:strict 数字的"裸数字部分"必须在润色文出现;
        // 仅当(plain 小整数 + 时间类单位月/期/个 时)允许 LLM 改写为中文(如 3 个月→三个月)。
        // 量化类单位(% / pp / ★ / ¥)必须严格保留。
        for (String n : origStrict) {
            String bare = stripUnit(n);
            if (polishAll.contains(bare)) continue;
            if (isPlainSmallInteger(bare) && hasTimeUnit(n)) continue;
            return Result.reject("数字丢失: " + n);
        }
        // 防新增:润色文不能出现原文没有的 strict 数字(带单位)
        for (String n : polishStrict) {
            if (!origStrict.contains(n) && !origAll.contains(stripUnit(n))) {
                return Result.reject("新增数字: " + n);
            }
        }

        // 防口语化丢失分类术语(可选,简化版只校验「建议/可考虑/推荐」之类不能出现「您」过多)
        long youCount = trimmed.chars().filter(c -> c == '您').count();
        if (youCount > 3) return Result.reject("过度客套(您 出现 " + youCount + " 次)");

        return Result.accept();
    }

    /** 时间类单位:月、个月、期、个 — 允许 plain 小整数被中文化 */
    private static boolean hasTimeUnit(String strictToken) {
        if (strictToken == null) return false;
        return strictToken.endsWith("月") || strictToken.endsWith("个月")
                || strictToken.endsWith("期") || strictToken.endsWith("个");
    }

    /** plain 小整数(无小数 < 100):允许 LLM 在中英文/汉字间改写,如 3 → 三 */
    private static boolean isPlainSmallInteger(String token) {
        if (token == null || !token.matches("\\d+")) return false;
        try { return Integer.parseInt(token) < 100; }
        catch (NumberFormatException e) { return false; }
    }

    /** 裸数字 token 抽取(包含 plain 整数和小数,不关心单位上下文) */
    private static Set<String> extractNumbers(String src) {
        Set<String> tokens = new HashSet<>();
        if (src == null) return tokens;
        Matcher m = NUMBER_TOKEN.matcher(src);
        while (m.find()) {
            tokens.add(normalizeToken(m.group()));
        }
        return tokens;
    }

    /**
     * Strict 数字抽取:数字后紧跟单位(%/pp/月/个/期/星/★/分点)→ 收 "数字+单位" 一体 token。
     * 这些是关键金融数字,LLM 不许改写。前后空格/标点都允许;比如 "3pp" 与 "3 pp" 等价。
     */
    private static final Pattern STRICT_NUMBER = Pattern.compile(
            "\\d+(?:\\.\\d+)?\\s*(?:%|pp|个月|月|个|期|星|★|分点)"
    );

    private static Set<String> extractStrictNumbers(String src) {
        Set<String> tokens = new HashSet<>();
        if (src == null) return tokens;
        Matcher m = STRICT_NUMBER.matcher(src);
        while (m.find()) {
            // 规范化:压缩空格 + 数字尾零去掉
            String t = m.group().replaceAll("\\s+", "");
            tokens.add(normalizeToken(t));
        }
        return tokens;
    }

    /** 把 strict token 拆出主体数字,用于跨原文/润色比较"裸数字是否一致" */
    private static String stripUnit(String token) {
        if (token == null) return "";
        Matcher num = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(token);
        if (num.find()) {
            return normalizeToken(num.group());
        }
        return token;
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
