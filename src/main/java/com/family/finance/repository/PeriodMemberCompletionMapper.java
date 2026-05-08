package com.family.finance.repository;

import com.family.finance.domain.period.PeriodMemberCompletion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface PeriodMemberCompletionMapper {

    @Select("SELECT COUNT(*) FROM period_member_completion WHERE period_id = #{periodId}")
    int countByPeriod(@Param("periodId") long periodId);

    @Insert("""
            INSERT IGNORE INTO period_member_completion (period_id, member_id)
            VALUES (#{periodId}, #{memberId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIgnore(PeriodMemberCompletion completion);

    @Delete("DELETE FROM period_member_completion WHERE period_id = #{periodId}")
    int deleteByPeriod(@Param("periodId") long periodId);
}
