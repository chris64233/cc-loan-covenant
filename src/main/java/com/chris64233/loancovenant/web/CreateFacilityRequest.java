package com.chris64233.loancovenant.web;

import com.chris64233.loancovenant.domain.CovenantOperator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 创建授信请求体。 */
public record CreateFacilityRequest(
        @NotBlank String customerName,
        @NotBlank String currency,
        @NotNull @DecimalMin(value = "0.0001", inclusive = true) BigDecimal totalLimit,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Valid List<CovenantRequest> covenants) {

    /** 财务契约配置请求体。 */
    public record CovenantRequest(
            @NotBlank String metricKey,
            @NotBlank String displayName,
            @NotNull CovenantOperator operator,
            @NotNull @DecimalMin(value = "0") BigDecimal threshold) {
    }
}
