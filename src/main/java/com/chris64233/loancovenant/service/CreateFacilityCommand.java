package com.chris64233.loancovenant.service;

import com.chris64233.loancovenant.domain.CovenantOperator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 创建授信命令。 */
public record CreateFacilityCommand(
        String customerName,
        String currency,
        BigDecimal totalLimit,
        LocalDate startDate,
        LocalDate endDate,
        List<CovenantSpec> covenants) {

    /** 单个财务契约配置。 */
    public record CovenantSpec(
            String metricKey,
            String displayName,
            CovenantOperator operator,
            BigDecimal threshold) {
    }
}
