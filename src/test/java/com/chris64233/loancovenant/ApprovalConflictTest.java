package com.chris64233.loancovenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import com.chris64233.loancovenant.api.dto.FacilityView;
import com.chris64233.loancovenant.api.dto.SnapshotView;
import com.chris64233.loancovenant.api.dto.SubmitDrawdownRequest;
import com.chris64233.loancovenant.api.dto.SubmitSnapshotRequest;
import com.chris64233.loancovenant.service.ConflictException;
import com.chris64233.loancovenant.service.DrawdownService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 审批期间状态变化的冲突：冻结授信、更正绑定快照都应使基于旧状态的批准返回冲突。
 */
class ApprovalConflictTest extends AbstractIntegrationTest {

    private final LocalDate period = LocalDate.of(2026, 6, 30);

    @Autowired
    private DrawdownService drawdownService;

    private FacilityView facility;
    private SnapshotView snapshot;

    @BeforeEach
    void setUp() {
        fixClock();
        facility = standardFacility();
        snapshot = healthySnapshot(facility.id(), period);
    }

    @Test
    void approvalConflictsWhenFacilityFrozenDuringPending() {
        drawdownService.submit(facility.id(),
                new SubmitDrawdownRequest("DD-FROZEN", snapshot.id(), new BigDecimal("1000")));
        facilityService.freeze(facility.id());

        assertThatThrownBy(() -> drawdownService.approve(facility.id(), "DD-FROZEN"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("FROZEN");
    }

    @Test
    void approvalConflictsWhenSnapshotCorrectedDuringPending() {
        SnapshotView v1 = healthySnapshot(facility.id(), period);
        drawdownService.submit(facility.id(),
                new SubmitDrawdownRequest("DD-CORRECTED", v1.id(), new BigDecimal("1000")));
        // 审批期间同一报告期提交更正版本。
        snapshotService.submit(facility.id(), new SubmitSnapshotRequest(period,
                Map.of("debtRatio", new BigDecimal("0.40"),
                        "currentRatio", new BigDecimal("2.50"))));

        assertThatThrownBy(() -> drawdownService.approve(facility.id(), "DD-CORRECTED"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("被更正");
    }

    @Test
    void closedFacilityCannotApprove() {
        drawdownService.submit(facility.id(),
                new SubmitDrawdownRequest("DD-CLOSED", snapshot.id(), new BigDecimal("1000")));
        facilityService.close(facility.id());
        assertThatThrownBy(() -> drawdownService.approve(facility.id(), "DD-CLOSED"))
                .isInstanceOf(ConflictException.class);
    }
}
