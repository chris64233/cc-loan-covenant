package com.chris64233.loancovenant.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** 财务快照信息。 */
public record SnapshotInfo(
        Long id,
        Long facilityId,
        LocalDate reportingPeriod,
        int versionNo,
        String status,
        Map<String, BigDecimal> metrics,
        Instant submittedAt) {
}
