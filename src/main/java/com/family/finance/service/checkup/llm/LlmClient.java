package com.family.finance.service.checkup.llm;

/**
 * LLM 客户端接口 · v0.2 FR-40c · 2026-05-10 修订(决策 20)
 *
 * <p>实现为 OpenAI 兼容 chat-completion 调用。注入 prompt → 返回单段文本。
 * 失败应抛 RuntimeException(由路由层转 fallback)。
 *
 * <p>2026-05-10 修订:method 从 {@code polish(system, user)} 改为 {@code chat(system, user)},
 * 因为新方向是综合诊断而非润色,语义更通用。
 */
public interface LlmClient {
    /** 厂商标识(qwen / deepseek)· 用于 audit + circuit breaker */
    String vendor();

    /** 提交 chat 请求(system + user 双消息),返回单段文本 */
    String chat(String systemPrompt, String userPrompt);

    /** 客户端是否当前可用(api key 已配置 + 未触发 breaker)*/
    boolean available();
}
