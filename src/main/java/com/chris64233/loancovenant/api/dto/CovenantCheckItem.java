package com.chris64233.loancovenant.api.dto;

import java.math.BigDecimal;

import com.chris64233.loancovenant.domain.CovenantOperator;

/**
 * 单个契约对某个快照的判断结果。
 */
public record CovenantCheckItem(
        String metricCode,
        String displayName,
        CovenantOperator operator,
        BigDecimal threshold,
        BigDecimal actualValue,
        boolean satisfied,
        String reason) {
}
