package com.family.finance.repository;

import com.family.finance.factview.FactBaseRow;
import com.family.finance.factview.FactFilter;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FactMapper {
    List<FactBaseRow> queryBase(FactFilter filter);
}
