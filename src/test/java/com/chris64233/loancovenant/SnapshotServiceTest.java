package com.chris64233.loancovenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chris64233.loancovenant.api.dto.CovenantCheckView;
import com.chris64233.loancovenant.api.dto.FacilityView;
import com.chris64233.loancovenant.api.dto.SnapshotView;
import com.chris64233.loancovenant.api.dto.SubmitSnapshotRequest;
import com.chris64233.loancovenant.domain.SnapshotStatus;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotServiceTest extends AbstractIntegrationTest {

    private FacilityView facility;
    private final LocalDate period = LocalDate.of(2026, 6, 30);

    @BeforeEach
    void setUp() {
        fixClock();
        facility = standardFacility();
    }

    @Test
    void sameReportPeriod_appendsVersionAndSupersedesOldOne() {
        SnapshotView v1 = healthySnapshot(facility.id(), period);
        SnapshotView v2 = snapshotService.submit(facility.id(),
                new SubmitSnapshotRequest(period,
                        Map.of("debtRatio", new BigDecimal("0.55"),
                                "currentRatio", new BigDecimal("2.00"))));

        assertThat(v1.version()).isEqualTo(1);
        assertThat(v2.version()).isEqualTo(2);
        // 旧版本不被覆盖：仍可查询，仅状态变为 SUPERSEDED；指标值保持原样。
        SnapshotView old = snapshotService.get(facility.id(), v1.id());
        assertThat(old.status()).isEqualTo(SnapshotStatus.SUPERSEDED);
        assertThat(old.metrics().get("debtRatio")).isEqualByComparingTo("0.60");
        SnapshotView current = snapshotService.get(facility.id(), v2.id());
        assertThat(current.status()).isEqualTo(SnapshotStatus.ACTIVE);
        // 报告期内两个版本都保留。
        List<SnapshotView> all = snapshotService.listPeriod(facility.id(), period);
        assertThat(all).hasSize(2);
        assertThat(all).extracting(SnapshotView::version).containsExactly(1, 2);
    }

    @Test
    void versionsOfDifferentPeriodsAreIndependent() {
        SnapshotView a = healthySnapshot(facility.id(), LocalDate.of(2026, 3, 31));
        SnapshotView b = healthySnapshot(facility.id(), LocalDate.of(2026, 6, 30));
        assertThat(a.version()).isEqualTo(1);
        assertThat(b.version()).isEqualTo(1);
    }

    @Test
    void covenantCheckReturnsPerCovenantResultsAndValidity() {
        SnapshotView snapshot = healthySnapshot(facility.id(), period);
        CovenantCheckView check = snapshotService.check(facility.id(), snapshot.id(), TODAY);

        assertThat(check.allCovenantsSatisfied()).isTrue();
        assertThat(check.effective()).isTrue();
        assertThat(check.snapshotCurrent()).isTrue();
        assertThat(check.items()).hasSize(2);
        assertThat(check.items()).allSatisfy(i -> assertThat(i.satisfied()).isTrue());
    }

    @Test
    void covenantCheckFailsWhenBreached() {
        SnapshotView snapshot = unhealthySnapshot(facility.id(), period);
        CovenantCheckView check = snapshotService.check(facility.id(), snapshot.id(), TODAY);
        assertThat(check.allCovenantsSatisfied()).isFalse();
        assertThat(check.items()).anySatisfy(i -> {
            assertThat(i.metricCode()).isEqualTo("debtRatio");
            assertThat(i.satisfied()).isFalse();
            assertThat(i.reason()).contains("不满足");
        });
    }

    @Test
    void covenantCheckMarksSupersededSnapshotAndExpiredFacility() {
        SnapshotView v1 = healthySnapshot(facility.id(), period);
        unhealthySnapshot(facility.id(), period); // v2，v1 被更正

        CovenantCheckView onOldVersion = snapshotService.check(
                facility.id(), v1.id(), TODAY);
        assertThat(onOldVersion.snapshotCurrent()).isFalse();

        LocalDate expired = TODAY.plusDays(31);
        CovenantCheckView expiredCheck = snapshotService.check(
                facility.id(), v1.id(), expired);
        assertThat(expiredCheck.effective()).isFalse();
    }

    @Test
    void missingMetricTreatedAsBreach() {
        SnapshotView sparse = snapshotService.submit(facility.id(),
                new SubmitSnapshotRequest(period,
                        Map.of("debtRatio", new BigDecimal("0.50"))));
        CovenantCheckView check = snapshotService.check(facility.id(), sparse.id(), TODAY);
        assertThat(check.allCovenantsSatisfied()).isFalse();
        assertThat(check.items()).filteredOn(i -> i.metricCode().equals("currentRatio"))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.actualValue()).isNull();
                    assertThat(i.satisfied()).isFalse();
                });
    }
}
