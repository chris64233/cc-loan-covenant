package com.chris64233.loancovenant.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 还款请求体。 */
public record RepayRequest(
        @NotNull @DecimalMin(value = "0.0001", inclusive = true) BigDecimal amount) {
}
