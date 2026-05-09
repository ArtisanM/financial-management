package com.family.finance.service.checkup.llm;

import com.family.finance.service.AuditLogService;
import com.family.finance.domain.audit.AuditLogType;
import com.family.finance.service.checkup.rule.Advice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * LLM 润色编排服务 · v0.2 FR-40c · 决策 6
 *
 * 流程:
 * <ol>
 *   <li>遍历可用 LlmClient(Qwen 主 / DeepSeek 备)按顺序尝试</li>
 *   <li>每个 client 调 {@link LlmClient#polish(String, String)}</li>
 *   <li>用 {@link OutputValidator} 校验返回内容</li>
 *   <li>校验通过 → 返回润色文案;全部失败 → 返回 empty + audit 记一次降级</li>
 * </ol>
 *
 * 缓存策略:同 advice + same prompt 在 24 小时内只调一次 LLM。
 * 简化:这里用 {@code java.util.concurrent.ConcurrentHashMap} 做内存缓存(单实例足够)。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LlmAdviceService {

    private final List<LlmClient> clients;
    private final AuditLogService auditLogService;

    private final java.util.concurrent.ConcurrentHashMap<String, CacheEntry> cache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long TTL_MS = 24L * 60 * 60 * 1000;

    /** 润色单条 advice;失败返回 empty(调用方应 fallback 到 raw 文案) */
    public Optional<Advice> polish(Long familyId, Long actorMemberId, Advice advice) {
        String cacheKey = advice.scope() + ":" + advice.ruleId() + ":" +
                (advice.accountId() == null ? "fam" : advice.accountId());
        CacheEntry hit = cache.get(cacheKey);
        if (hit != null && System.currentTimeMillis() - hit.timestamp < TTL_MS) {
            return Optional.of(advice.withPolished(hit.title, hit.body));
        }

        String system = PromptBuilder.systemFor(advice.dimension());
        String user = PromptBuilder.userFor(advice);

        for (LlmClient client : clients) {
            if (!client.available()) continue;
            try {
                String polished = client.polish(system, user);
                OutputValidator.Result vr = OutputValidator.check(advice, polished);
                if (!vr.accepted()) {
                    log.warn("LLM[{}] 输出未通过校验:{}", client.vendor(), vr.reason());
                    continue;
                }
                cache.put(cacheKey, new CacheEntry(advice.rawTitle(), polished, System.currentTimeMillis()));
                return Optional.of(advice.withPolished(advice.rawTitle(), polished));
            } catch (Exception e) {
                log.warn("LLM[{}] 调用失败: {}", client.vendor(), e.getMessage());
            }
        }

        // 全部 client 失败
        if (familyId != null) {
            try {
                auditLogService.record(familyId, actorMemberId, AuditLogType.LLM_DEGRADED,
                        "advice", advice.accountId(),
                        "LLM 全部 client 失败/无可用,使用 raw 文案 [" + advice.ruleId() + "]");
            } catch (Exception ignore) {
                // audit 失败不阻塞主流程
            }
        }
        return Optional.empty();
    }

    private record CacheEntry(String title, String body, long timestamp) {}
}
