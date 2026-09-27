package com.chris64233.loancovenant.api.dto;

import java.math.BigDecimal;

import com.chris64233.loancovenant.domain.CovenantOperator;

public record CovenantView(
        Long id,
        String metricCode,
        String displayName,
        CovenantOperator operator,
        BigDecimal threshold) {
}
