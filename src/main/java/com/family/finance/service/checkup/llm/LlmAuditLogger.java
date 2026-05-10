package com.family.finance.service.checkup.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v0.2 · LLM 全交互日志 · 2026-05-10
 *
 * <p>每次 LLM 调用(qwen / deepseek)都通过此 logger 输出多行块,便于:
 * <ul>
 *   <li>{@code journalctl -u finance | grep LLM_AUDIT} 过滤</li>
 *   <li>逐字检查 prompt 与 response,排查脱敏 / 数字保真问题</li>
 *   <li>看每次调用的 elapsed_ms,排查 SLA</li>
 * </ul>
 *
 * <p>独立 logger name = {@code llm.audit},生产环境想关掉只需在 logback 配置里
 * 把 {@code <logger name="llm.audit" level="WARN"/>} 即可静音。
 */
public final class LlmAuditLogger {

    private static final Logger LOG = LoggerFactory.getLogger("llm.audit");

    private LlmAuditLogger() {}

    /**
     * 记录一次完整 LLM 调用。
     *
     * @param vendor      qwen / deepseek
     * @param scope       FAMILY / ACCOUNT
     * @param familyId    家庭 id
     * @param entityId    账户 id(scope=FAMILY 时为 null)
     * @param systemPrompt LLM system 消息全文
     * @param userPrompt  LLM user 消息全文
     * @param response    LLM 返回原始文本(可能为 null 表示调用抛异常)
     * @param elapsedMs   总耗时(含网络 + 推理)
     * @param accepted    OutputValidator 是否接受
     * @param rejectReason 不接受时的具体理由(为 null 表示接受)
     * @param error       调用抛异常时的消息(为 null 表示成功返回)
     */
    public static void log(String vendor, String scope, Long familyId, Long entityId,
                           String systemPrompt, String userPrompt,
                           String response, long elapsedMs,
                           boolean accepted, String rejectReason, String error) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append('\n');
        sb.append("===== LLM_AUDIT [").append(vendor).append("] ");
        sb.append("scope=").append(scope);
        sb.append(" family=").append(familyId);
        sb.append(" entity=").append(entityId == null ? "null" : entityId);
        sb.append(" elapsed=").append(elapsedMs).append("ms");
        sb.append(" SLA=").append(slaLabel(elapsedMs));
        sb.append(' ');
        if (error != null) {
            sb.append("ERROR ").append('\n');
            sb.append("--- error ---").append('\n');
            sb.append(error).append('\n');
        } else {
            sb.append("ok=").append(accepted);
            if (!accepted && rejectReason != null) sb.append(" reject=\"").append(rejectReason).append("\"");
            sb.append('\n');
        }
        sb.append("--- system prompt (").append(systemPrompt == null ? 0 : systemPrompt.length()).append(" chars) ---").append('\n');
        sb.append(systemPrompt == null ? "(null)" : systemPrompt).append('\n');
        sb.append("--- user prompt (").append(userPrompt == null ? 0 : userPrompt.length()).append(" chars) ---").append('\n');
        sb.append(userPrompt == null ? "(null)" : userPrompt).append('\n');
        sb.append("--- response (").append(response == null ? 0 : response.length()).append(" chars) ---").append('\n');
        sb.append(response == null ? "(null)" : response).append('\n');
        sb.append("===== /LLM_AUDIT [").append(vendor).append("] =====");
        LOG.info(sb.toString());
    }

    /** 简单 SLA 标签:< 3s OK / 3-8s SLOW / > 8s VERY_SLOW */
    private static String slaLabel(long ms) {
        if (ms < 3000) return "OK";
        if (ms < 8000) return "SLOW";
        return "VERY_SLOW";
    }
}
