package com.chris64233.loancovenant.api.dto;

import java.math.BigDecimal;

import com.chris64233.loancovenant.domain.CovenantOperator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建/更新契约配置项。
 */
public record CovenantSpec(
        @NotBlank String metricCode,
        @NotBlank String displayName,
        @NotNull CovenantOperator operator,
        @NotNull BigDecimal threshold) {
}
