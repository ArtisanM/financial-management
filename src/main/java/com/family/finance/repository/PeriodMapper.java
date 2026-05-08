package com.family.finance.repository;

import com.family.finance.domain.period.Period;
import com.family.finance.domain.period.PeriodType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PeriodMapper {

    @Select("""
            SELECT id, family_id, period_type, period_start, period_end, status, closed_at, created_at
              FROM period
             WHERE id = #{id}
            """)
    Optional<Period> findById(@Param("id") long id);

    @Select("""
            SELECT id, family_id, period_type, period_start, period_end, status, closed_at, created_at
              FROM period
             WHERE family_id = #{familyId}
               AND status = 'OPEN'
             ORDER BY period_start DESC
             LIMIT 1
            """)
    Optional<Period> findCurrentOpen(@Param("familyId") long familyId);

    @Select("""
            SELECT id, family_id, period_type, period_start, period_end, status, closed_at, created_at
              FROM period
             WHERE family_id = #{familyId}
               AND period_type = #{periodType}
               AND period_start = #{periodStart}
            """)
    Optional<Period> findByNatural(@Param("familyId") long familyId,
                                   @Param("periodType") PeriodType periodType,
                                   @Param("periodStart") LocalDate periodStart);

    @Select("""
            SELECT id, family_id, period_type, period_start, period_end, status, closed_at, created_at
              FROM period
             WHERE family_id = #{familyId}
               AND period_start BETWEEN #{from} AND #{to}
             ORDER BY period_start DESC
            """)
    List<Period> findRange(@Param("familyId") long familyId,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to);

    @Select("""
            SELECT id, family_id, period_type, period_start, period_end, status, closed_at, created_at
              FROM period
             WHERE family_id = #{familyId}
             ORDER BY period_start DESC
             LIMIT #{limit}
            """)
    List<Period> findLatest(@Param("familyId") long familyId, @Param("limit") int limit);

    @Select("""
            SELECT id, family_id, period_type, period_start, period_end, status, closed_at, created_at
              FROM period
             WHERE family_id = #{familyId}
             ORDER BY period_start
            """)
    List<Period> findAllByFamily(@Param("familyId") long familyId);

    @Insert("""
            INSERT INTO period (family_id, period_type, period_start, period_end, status)
            VALUES (#{familyId}, #{periodType}, #{periodStart}, #{periodEnd}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Period period);

    @Update("""
            UPDATE period
               SET status = 'CLOSED',
                   closed_at = NOW(3)
             WHERE id = #{id}
               AND family_id = #{familyId}
            """)
    int close(@Param("familyId") long familyId, @Param("id") long id);

    @Update("""
            UPDATE period
               SET status = 'OPEN',
                   closed_at = NULL
             WHERE id = #{id}
               AND family_id = #{familyId}
            """)
    int reopen(@Param("familyId") long familyId, @Param("id") long id);
}
