package com.family.finance.service;

import com.family.finance.domain.family.Family;
import com.family.finance.repository.FamilyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FamilyService {

    /** v0.2 FR-1/FR-34:固定预设白名单 — icon{1..4} 对应 static/img/presets/iconN-{96,180,192,512}.png */
    public static final Set<String> SUPPORTED_LOGO_PRESETS = Set.of("icon1", "icon2", "icon3", "icon4");
    public static final String DEFAULT_LOGO_PRESET = "icon2";

    private final FamilyMapper familyMapper;

    public List<Family> findAll() {
        return familyMapper.findAll();
    }

    public Family require(long familyId) {
        return familyMapper.findById(familyId)
                .orElseThrow(() -> new IllegalArgumentException("家庭不存在: " + familyId));
    }

    public void updateBrandLogo(long familyId, String logoPath) {
        familyMapper.updateLogoPath(familyId, logoPath);
    }

    /**
     * 切换品牌预设图标 — 一并清空自定义 logo_path,实现"预设赢一切统一"。
     * @throws IllegalArgumentException 当 preset ∉ SUPPORTED_LOGO_PRESETS
     */
    public void updateLogoPreset(long familyId, String preset) {
        if (preset == null || !SUPPORTED_LOGO_PRESETS.contains(preset)) {
            throw new IllegalArgumentException("非法预设代码: " + preset);
        }
        familyMapper.updateLogoPreset(familyId, preset);
    }
}
