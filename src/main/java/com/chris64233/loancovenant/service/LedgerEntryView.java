package com.chris64233.loancovenant.service;

import java.math.BigDecimal;
import java.time.Instant;

/** 不可变额度台账流水视图。 */
public record LedgerEntryView(
        Long id,
        Long facilityId,
        Long drawdownId,
        String drawdownBusinessNo,
        String type,
        BigDecimal amount,
        BigDecimal usedAmountAfter,
        Instant occurredAt) {
}
