package com.chris64233.loancovenant.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 创建授信请求。
 */
public record CreateFacilityRequest(
        @NotBlank String customerName,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @Positive BigDecimal totalLimit,
        @NotNull LocalDate effectiveFrom,
        @NotNull LocalDate effectiveTo,
        @NotEmpty @Valid List<CovenantSpec> covenants) {
}
