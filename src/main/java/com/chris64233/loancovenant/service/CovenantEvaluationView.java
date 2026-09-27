package com.chris64233.loancovenant.service;

import java.time.LocalDate;
import java.util.List;

/** 契约判断视图：某授信在某快照版本上的逐项契约结果。 */
public record CovenantEvaluationView(
        Long facilityId,
        Long snapshotId,
        LocalDate reportingPeriod,
        int versionNo,
        String snapshotStatus,
        boolean allSatisfied,
        List<CovenantCheck> checks) {
}
