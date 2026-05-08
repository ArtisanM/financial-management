package com.family.finance.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PeriodReopenLogMapper {

    @Insert("""
            INSERT INTO period_reopen_log (period_id, reopened_by, reason)
            VALUES (#{periodId}, #{reopenedBy}, #{reason})
            """)
    int insert(@Param("periodId") long periodId,
               @Param("reopenedBy") Long reopenedBy,
               @Param("reason") String reason);
}
