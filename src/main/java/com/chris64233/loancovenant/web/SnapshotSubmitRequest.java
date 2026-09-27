package com.chris64233.loancovenant.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** 提交财务快照请求体。 */
public record SnapshotSubmitRequest(
        @NotNull LocalDate reportingPeriod,
        @NotEmpty Map<String, BigDecimal> metrics) {
}
