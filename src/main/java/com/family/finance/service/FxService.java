package com.family.finance.service;

import com.family.finance.config.AppProperties;
import com.family.finance.domain.audit.AuditLogType;
import com.family.finance.domain.family.Family;
import com.family.finance.domain.fx.FxRate;
import com.family.finance.domain.period.Period;
import com.family.finance.repository.FxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FxService {

    private static final Set<String> SUPPORTED_QUOTES = Set.of("USD", "HKD");

    private final FxMapper fxMapper;
    private final FamilyService familyService;
    private final PeriodService periodService;
    private final AuditLogService auditLogService;
    private final AppProperties props;

    public List<FxRate> recent(long familyId, int limit) {
        return fxMapper.findLatestByFamily(familyId, limit);
    }

    /** 后台 cron / 手动按钮 都走这里:为指定家庭、指定周期拉所有 quote 币种的汇率 */
    public int fetchAndStore(long familyId, long periodId) {
        Family family = familyService.require(familyId);
        String base = family.getBaseCurrency();
        int ok = 0;
        RestClient client = RestClient.builder()
                .baseUrl(props.fxApiBase())
                .requestFactory(httpRequestFactory())
                .build();
        for (String quote : SUPPORTED_QUOTES) {
            try {
                BigDecimal rate = fetchRate(client, base, quote);
                fxMapper.upsert(familyId, base, quote, periodId, rate, "exchangerate.host");
                ok++;
                log.info("[Fx] fetched {} {}->{} = {}", periodId, base, quote, rate);
            } catch (Exception e) {
                log.warn("[Fx] failed {} {}->{}: {}", periodId, base, quote, e.toString());
                auditLogService.systemAlert(familyId, AuditLogType.FX_FETCH,
                        "汇率拉取失败:%s→%s · %s".formatted(base, quote, e.getClass().getSimpleName()));
            }
        }
        return ok;
    }

    /** 手动覆盖(/admin/fx 表单)*/
    public void manualOverride(long familyId, long periodId, String quoteCurrency, BigDecimal rate, Long actorMemberId) {
        Family family = familyService.require(familyId);
        fxMapper.upsert(familyId, family.getBaseCurrency(), quoteCurrency, periodId, rate, "manual");
        auditLogService.write(familyId, actorMemberId, AuditLogType.FX_FETCH,
                "fx_rate", periodId,
                "手动覆盖 %s→%s = %s @ period#%d".formatted(family.getBaseCurrency(), quoteCurrency, rate, periodId),
                Map.of("base", family.getBaseCurrency(), "quote", quoteCurrency, "rate", rate, "periodId", periodId));
    }

    private BigDecimal fetchRate(RestClient client, String base, String quote) {
        Map<?, ?> body = client.get()
                .uri("/latest?base={base}&symbols={quote}", base, quote)
                .retrieve()
                .body(Map.class);
        if (body == null) throw new IllegalStateException("empty response");
        Object rates = body.get("rates");
        if (!(rates instanceof Map<?, ?> m)) throw new IllegalStateException("rates missing");
        Object r = m.get(quote);
        if (r == null) throw new IllegalStateException("quote " + quote + " missing");
        return new BigDecimal(r.toString());
    }

    private org.springframework.http.client.ClientHttpRequestFactory httpRequestFactory() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) props.fxFetchTimeoutMs());
        f.setReadTimeout((int) props.fxFetchTimeoutMs());
        return f;
    }

    /** 当前 OPEN 周期(若有)+ 上一个 CLOSED 周期都拉一下,作为初始/补全场景 */
    public void fetchForLatestPeriods(long familyId) {
        Period current = periodService.findCurrentOpen(familyId).orElse(null);
        if (current != null) fetchAndStore(familyId, current.getId());
    }
}
