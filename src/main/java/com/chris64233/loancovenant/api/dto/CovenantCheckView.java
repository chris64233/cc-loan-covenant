package com.chris64233.loancovenant.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 指定快照版本的契约判断结果，同时给出授信有效期判断。
 */
public record CovenantCheckView(
        Long facilityId,
        Long snapshotId,
        LocalDate reportPeriod,
        int snapshotVersion,
        boolean snapshotCurrent,
        boolean allCovenantsSatisfied,
        boolean effective,
        LocalDate evaluatedOn,
        List<CovenantCheckItem> items) {
}
