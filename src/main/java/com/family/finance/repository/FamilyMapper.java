package com.family.finance.repository;

import com.family.finance.domain.family.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FamilyMapper {

    @Select("""
            SELECT id, name, brand_text, logo_path, logo_preset, base_currency, period_type, created_at, updated_at
              FROM family
             ORDER BY id
            """)
    List<Family> findAll();

    @Select("""
            SELECT id, name, brand_text, logo_path, logo_preset, base_currency, period_type, created_at, updated_at
              FROM family
             WHERE id = #{id}
            """)
    Optional<Family> findById(@Param("id") long id);

    @Update("""
            UPDATE family
               SET name = #{name},
                   brand_text = #{brandText},
                   base_currency = #{baseCurrency},
                   period_type = #{periodType}
             WHERE id = #{id}
            """)
    int update(Family family);

    @Update("UPDATE family SET logo_path = #{logoPath} WHERE id = #{familyId}")
    int updateLogoPath(@Param("familyId") long familyId, @Param("logoPath") String logoPath);

    /**
     * v0.2 FR-1/FR-34:点击预设按钮 = 切预设 + 一并清空自定义 logo_path,
     * 这样 web favicon / iOS apple-touch / PWA manifest 三处全用同一张预设(预设赢一切统一)。
     */
    @Update("UPDATE family SET logo_preset = #{preset}, logo_path = NULL WHERE id = #{familyId}")
    int updateLogoPreset(@Param("familyId") long familyId, @Param("preset") String preset);
}
