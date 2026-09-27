package com.chris64233.loancovenant.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 提交财务快照：同一报告期重复提交会追加为新版本，旧版本变为 SUPERSEDED。
 */
public record SubmitSnapshotRequest(
        @NotNull LocalDate reportPeriod,
        @NotEmpty Map<String, BigDecimal> metrics) {
}
