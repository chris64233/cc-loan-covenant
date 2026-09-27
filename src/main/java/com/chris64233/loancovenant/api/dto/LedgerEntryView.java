package com.chris64233.loancovenant.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.chris64233.loancovenant.domain.LedgerEntryType;

public record LedgerEntryView(
        Long id,
        Long facilityId,
        String drawdownBusinessNo,
        LedgerEntryType entryType,
        BigDecimal deltaUsed,
        BigDecimal usedAfter,
        Instant recordedAt) {
}
