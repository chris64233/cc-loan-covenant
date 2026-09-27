package com.chris64233.loancovenant.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.chris64233.loancovenant.domain.DrawdownStatus;

public record DrawdownView(
        Long id,
        long version,
        String businessNo,
        Long facilityId,
        Long snapshotId,
        java.time.LocalDate snapshotReportPeriod,
        int snapshotVersion,
        BigDecimal amount,
        DrawdownStatus status,
        BigDecimal repaidAmount,
        BigDecimal outstandingAmount,
        String approvalEvidence,
        Instant approvedAt,
        Instant cancelledAt,
        Instant disbursedAt,
        Instant lastRepaidAt,
        Instant createdAt) {
}
