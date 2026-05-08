package com.family.finance.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface MetricsRecomputeLogMapper {

    /** 单次完整记录:开始时间用 Java 端的 startedAt,结束时计算 duration */
    @Insert("""
            INSERT INTO metrics_recompute_log
                (family_id, period_id, started_at, completed_at, duration_ms,
                 identity_ok, deviation, error_message)
            VALUES
                (#{familyId}, #{periodId}, #{startedAt}, #{completedAt}, #{durationMs},
                 #{identityOk}, #{deviation}, #{errorMessage})
            """)
    int insert(@Param("familyId") long familyId,
               @Param("periodId") long periodId,
               @Param("startedAt") LocalDateTime startedAt,
               @Param("completedAt") LocalDateTime completedAt,
               @Param("durationMs") int durationMs,
               @Param("identityOk") Boolean identityOk,
               @Param("deviation") BigDecimal deviation,
               @Param("errorMessage") String errorMessage);
}
