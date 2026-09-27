package com.chris64233.loancovenant.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 提交提款申请。snapshotId 必须指向一个明确的快照版本。
 */
public record SubmitDrawdownRequest(
        @NotBlank String businessNo,
        @NotNull Long snapshotId,
        @NotNull @Positive BigDecimal amount) {
}
