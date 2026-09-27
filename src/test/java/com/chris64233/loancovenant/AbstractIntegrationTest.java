package com.chris64233.loancovenant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;

import com.chris64233.loancovenant.api.dto.CovenantSpec;
import com.chris64233.loancovenant.api.dto.CreateFacilityRequest;
import com.chris64233.loancovenant.api.dto.FacilityView;
import com.chris64233.loancovenant.api.dto.SnapshotView;
import com.chris64233.loancovenant.api.dto.SubmitSnapshotRequest;
import com.chris64233.loancovenant.domain.CovenantOperator;
import com.chris64233.loancovenant.service.FacilityService;
import com.chris64233.loancovenant.service.SnapshotService;

import static org.mockito.Mockito.when;

@SpringBootTest
abstract class AbstractIntegrationTest {

    protected static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    @Autowired
    protected FacilityService facilityService;

    @Autowired
    protected SnapshotService snapshotService;

    @MockitoBean
    protected Clock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // 关闭引用约束后清空全部表并重置自增主键，保证测试隔离。
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : List.of("limit_ledger_entry", "drawdown_request",
                "snapshot_metric", "financial_snapshot", "financial_covenant",
                "credit_facility")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    protected void fixClock() {
        when(clock.instant()).thenReturn(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        // LocalDate.now(clock) 内部使用 clock.instant() + zone
    }

    protected FacilityView createFacility(BigDecimal totalLimit,
                                          LocalDate from, LocalDate to,
                                          List<CovenantSpec> covenants) {
        return facilityService.create(new CreateFacilityRequest(
                "示例企业", "CNY", totalLimit, from, to, covenants));
    }

    /** 默认授信：1000 万、覆盖 TODAY、负债率<=70%、流动比率>=1.5。 */
    protected FacilityView standardFacility() {
        return createFacility(new BigDecimal("10000000.0000"),
                TODAY.minusDays(30), TODAY.plusDays(30),
                List.of(
                        new CovenantSpec("debtRatio", "资产负债率",
                                CovenantOperator.AT_MOST, new BigDecimal("0.70")),
                        new CovenantSpec("currentRatio", "流动比率",
                                CovenantOperator.AT_LEAST, new BigDecimal("1.50"))));
    }

    protected SnapshotView healthySnapshot(Long facilityId, LocalDate period) {
        return snapshotService.submit(facilityId, new SubmitSnapshotRequest(period,
                Map.of("debtRatio", new BigDecimal("0.60"),
                        "currentRatio", new BigDecimal("1.80"))));
    }

    protected SnapshotView unhealthySnapshot(Long facilityId, LocalDate period) {
        return snapshotService.submit(facilityId, new SubmitSnapshotRequest(period,
                Map.of("debtRatio", new BigDecimal("0.80"),
                        "currentRatio", new BigDecimal("1.80"))));
    }
}
