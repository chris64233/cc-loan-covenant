package com.chris64233.loancovenant.service;

import java.math.BigDecimal;

/** 契约配置信息。 */
public record CovenantInfo(
        Long id,
        String metricKey,
        String displayName,
        String operator,
        BigDecimal threshold) {
}
