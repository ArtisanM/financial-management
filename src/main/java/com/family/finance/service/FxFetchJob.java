package com.family.finance.service;

import com.family.finance.domain.family.Family;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 每月 1 日 02:30 拉取所有家庭最新汇率(FR-15)。
 * v0.1 仅支持 USD / HKD,基础币种来自 family.base_currency。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FxFetchJob {

    private final FamilyService familyService;
    private final FxService fxService;

    @Scheduled(cron = "0 30 2 1 * ?", zone = "Asia/Shanghai")
    public void runMonthly() {
        List<Family> families = familyService.findAll();
        log.info("[FxFetchJob] tick · families={}", families.size());
        for (Family f : families) {
            try {
                fxService.fetchForLatestPeriods(f.getId());
            } catch (Exception e) {
                log.warn("[FxFetchJob] failed familyId={}: {}", f.getId(), e.toString());
            }
        }
    }
}
