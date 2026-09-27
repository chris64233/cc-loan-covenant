package com.chris64233.loancovenant.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 授信余额视图（含契约配置）。 */
public record FacilityBalanceView(
        Long facilityId,
        String customerName,
        String currency,
        BigDecimal totalLimit,
        BigDecimal usedAmount,
        BigDecimal availableAmount,
        LocalDate startDate,
        LocalDate endDate,
        boolean frozen,
        boolean withinValidity,
        long version,
        List<CovenantInfo> covenants) {
}
