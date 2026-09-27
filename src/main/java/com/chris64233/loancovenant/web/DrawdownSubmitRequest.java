package com.chris64233.loancovenant.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 提交提款申请请求体。 */
public record DrawdownSubmitRequest(
        @NotBlank String businessNo,
        @NotNull Long snapshotId,
        @NotNull @DecimalMin(value = "0.0001", inclusive = true) BigDecimal amount) {
}
