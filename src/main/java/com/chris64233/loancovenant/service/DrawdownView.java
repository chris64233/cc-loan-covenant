package com.chris64233.loancovenant.service;

import java.math.BigDecimal;
import java.time.Instant;

/** 提款状态视图。 */
public record DrawdownView(
        Long id,
        String businessNo,
        Long facilityId,
        String currency,
        Long snapshotId,
        String snapshotStatus,
        BigDecimal amount,
        String status,
        String decisionBasis,
        String rejectionReason,
        BigDecimal repaidAmount,
        BigDecimal outstandingAmount,
        Instant createdAt,
        Instant approvedAt,
        Instant cancelledAt,
        Instant disbursedAt,
        Instant settledAt) {
}
