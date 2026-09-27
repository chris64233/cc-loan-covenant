package com.chris64233.loancovenant.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import com.chris64233.loancovenant.domain.SnapshotStatus;

public record SnapshotView(
        Long id,
        Long facilityId,
        LocalDate reportPeriod,
        int version,
        SnapshotStatus status,
        Map<String, BigDecimal> metrics,
        Instant submittedAt) {
}
