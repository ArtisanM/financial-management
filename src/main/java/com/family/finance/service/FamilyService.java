package com.family.finance.service;

import com.family.finance.domain.family.Family;
import com.family.finance.repository.FamilyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyService {

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
}
