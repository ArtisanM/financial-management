package com.family.finance.service.checkup.llm;

/**
 * LLM 客户端接口 · v0.2 FR-40c
 *
 * 实现为 OpenAI 兼容 chat-completion 调用。注入 prompt → 返回 polished text。
 * 失败应抛 RuntimeException(由路由层转 fallback)。
 */
public interface LlmClient {
    /** 厂商标识(qwen / deepseek)· 用于 audit + circuit breaker */
    String vendor();

    /** 提交润色请求,返回单段中文文本(不含数字编辑过的内容) */
    String polish(String systemPrompt, String userPrompt);

    /** 客户端是否当前可用(api key 已配置 + 未触发 breaker)*/
    boolean available();
}
