package com.chris64233.loancovenant.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.chris64233.loancovenant.domain.FacilityStatus;

public record FacilityView(
        Long id,
        long version,
        String customerName,
        String currency,
        BigDecimal totalLimit,
        BigDecimal usedLimit,
        BigDecimal availableLimit,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        FacilityStatus status,
        List<CovenantView> covenants) {
}
