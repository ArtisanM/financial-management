package com.family.finance.repository;

import com.family.finance.domain.flow.CashFlowCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CashFlowCategoryMapper {

    @Select("""
            SELECT code, display_name, kind, sort_order
              FROM cash_flow_category
             ORDER BY sort_order, code
            """)
    List<CashFlowCategory> listOrdered();
}
