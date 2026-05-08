package com.family.finance.repository;

import com.family.finance.domain.fx.FxRate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface FxMapper {

    @Select("""
            SELECT id, family_id, base_currency, quote_currency, period_id, rate, source, fetched_at
              FROM fx_rate
             WHERE family_id = #{familyId}
             ORDER BY period_id DESC, quote_currency
             LIMIT #{limit}
            """)
    List<FxRate> findLatestByFamily(@Param("familyId") long familyId, @Param("limit") int limit);

    @Select("""
            SELECT id, family_id, base_currency, quote_currency, period_id, rate, source, fetched_at
              FROM fx_rate
             WHERE family_id = #{familyId}
             ORDER BY period_id, quote_currency
            """)
    List<FxRate> findAllByFamily(@Param("familyId") long familyId);

    @Select("""
            SELECT id, family_id, base_currency, quote_currency, period_id, rate, source, fetched_at
              FROM fx_rate
             WHERE family_id = #{familyId}
               AND base_currency = #{baseCurrency}
               AND quote_currency = #{quoteCurrency}
               AND period_id = #{periodId}
            """)
    Optional<FxRate> findOne(@Param("familyId") long familyId,
                             @Param("baseCurrency") String baseCurrency,
                             @Param("quoteCurrency") String quoteCurrency,
                             @Param("periodId") long periodId);

    /** UPSERT — 同 (familyId, base, quote, period) 触发 ON DUPLICATE KEY,覆盖 rate/source/fetched_at */
    @Insert("""
            INSERT INTO fx_rate (family_id, base_currency, quote_currency, period_id, rate, source)
            VALUES (#{familyId}, #{baseCurrency}, #{quoteCurrency}, #{periodId}, #{rate}, #{source})
            ON DUPLICATE KEY UPDATE
                rate = VALUES(rate),
                source = VALUES(source),
                fetched_at = CURRENT_TIMESTAMP(3)
            """)
    int upsert(@Param("familyId") long familyId,
               @Param("baseCurrency") String baseCurrency,
               @Param("quoteCurrency") String quoteCurrency,
               @Param("periodId") long periodId,
               @Param("rate") BigDecimal rate,
               @Param("source") String source);
}
