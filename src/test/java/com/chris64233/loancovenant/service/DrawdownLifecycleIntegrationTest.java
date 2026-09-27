package com.chris64233.loancovenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.loancovenant.domain.CovenantOperator;
import com.chris64233.loancovenant.testsupport.MutableClock;
import com.chris64233.loancovenant.testsupport.TestClockConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestClockConfig.class)
class DrawdownLifecycleIntegrationTest {

    @Autowired FacilityService facilityService;
    @Autowired SnapshotService snapshotService;
    @Autowired DrawdownService drawdownService;
    @Autowired MutableClock clock;

    private Long facilityId;

    private CreateFacilityCommand facility(BigDecimal limit, LocalDate end) {
        return new CreateFacilityCommand(
                "示例企业", "CNY", limit,
                LocalDate.of(2026, 1, 1), end,
                List.of(
                        new CreateFacilityCommand.CovenantSpec(
                                "CURRENT_RATIO", "流动比率", CovenantOperator.GTE,
                                new BigDecimal("1.50")),
                        new CreateFacilityCommand.CovenantSpec(
                                "DEBT_TO_EBITDA", "资产负债率指标", CovenantOperator.LTE,
                                new BigDecimal("3.00"))));
    }

    private SnapshotInfo healthySnapshot(LocalDate period) {
        return snapshotService.submit(new SnapshotService.SubmitCommand(
                facilityId, period,
                Map.of("CURRENT_RATIO", new BigDecimal("1.80"),
                        "DEBT_TO_EBITDA", new BigDecimal("2.50"))));
    }

    private SnapshotInfo breachSnapshot(LocalDate period) {
        return snapshotService.submit(new SnapshotService.SubmitCommand(
                facilityId, period,
                Map.of("CURRENT_RATIO", new BigDecimal("1.20"),
                        "DEBT_TO_EBITDA", new BigDecimal("2.50"))));
    }

    @BeforeEach
    void setUp() {
        clock.setInstant(java.time.Instant.parse("2026-06-30T10:00:00Z"));
        facilityId = facilityService.create(
                facility(new BigDecimal("1000.0000"), LocalDate.of(2026, 12, 31))).facilityId();
    }

    // ------------------------------------------------------- 快照追加

    @Test
    void snapshot_versions_append_and_never_overwrite() {
        LocalDate period = LocalDate.of(2026, 3, 31);
        SnapshotInfo v1 = healthySnapshot(period);
        SnapshotInfo v2 = snapshotService.submit(new SnapshotService.SubmitCommand(
                facilityId, period,
                Map.of("CURRENT_RATIO", new BigDecimal("2.00"),
                        "DEBT_TO_EBITDA", new BigDecimal("2.00"))));

        assertThat(v1.versionNo()).isEqualTo(1);
        assertThat(v2.versionNo()).isEqualTo(2);
        List<SnapshotInfo> all = snapshotService.listByFacility(facilityId);
        assertThat(all).hasSize(2);
        assertThat(all).extracting(SnapshotInfo::versionNo).containsExactly(2, 1);
        // 旧版本不删除也不可改：状态转为 SUPERSEDED，指标值保留原样。
        SnapshotInfo stale = all.get(1);
        assertThat(stale.status()).isEqualTo("SUPERSEDED");
        assertThat(stale.metrics().get("CURRENT_RATIO")).isEqualByComparingTo("1.80");
    }

    // ------------------------------------------------------- 完整生命周期

    @Test
    void approve_cancel_releases_limit_and_writes_immutable_ledger() {
        SnapshotInfo snap = healthySnapshot(LocalDate.of(2026, 3, 31));
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-001", facilityId, snap.id(), new BigDecimal("600.0000")));

        DrawdownView approved = drawdownService.approve("DD-001");
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.decisionBasis()).contains("snapshotVersionNo").contains("covenantChecks");
        assertThat(approved.decisionBasis())
                .contains("\"usedAmountBefore\":0")
                .contains("\"availableAmountBefore\":1000.0000")
                .contains("\"requestedAmount\":600.0000");
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("600.0000");
        assertThat(facilityService.balance(facilityId).availableAmount()).isEqualByComparingTo("400.0000");

        // 已批准未拨付 → 取消释放额度。
        DrawdownView cancelled = drawdownService.cancel("DD-001");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("0");

        List<LedgerEntryView> ledger = drawdownService.ledger(facilityId);
        assertThat(ledger).hasSize(2);
        assertThat(ledger).extracting(LedgerEntryView::type)
                .containsExactly("DRAWDOWN_APPROVED", "DRAWDOWN_CANCELLED");
        assertThat(ledger.get(0).usedAmountAfter()).isEqualByComparingTo("600.0000");
        assertThat(ledger.get(1).usedAmountAfter()).isEqualByComparingTo("0");
        // 台账 ID 单调、流水恒为正金额。
        assertThat(ledger).extracting(LedgerEntryView::id)
                .containsExactly(ledger.get(0).id(), ledger.get(1).id());
        assertThat(ledger).allMatch(e -> e.amount().signum() > 0);
    }

    @Test
    void disbursed_drawdown_can_only_be_reduced_by_repayment() {
        SnapshotInfo snap = healthySnapshot(LocalDate.of(2026, 3, 31));
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-002", facilityId, snap.id(), new BigDecimal("300.0000")));
        drawdownService.approve("DD-002");
        assertThat(drawdownService.disburse("DD-002").status()).isEqualTo("DISBURSED");

        // 已拨付不允许取消。
        assertThatThrownBy(() -> drawdownService.cancel("DD-002"))
                .isInstanceOf(ConflictException.class);

        // 部分还款释放额度，最终结清。
        DrawdownView partial = drawdownService.repay("DD-002", new BigDecimal("100.0000"));
        assertThat(partial.status()).isEqualTo("DISBURSED");
        assertThat(partial.outstandingAmount()).isEqualByComparingTo("200.0000");
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("200.0000");

        DrawdownView settled = drawdownService.repay("DD-002", new BigDecimal("200.0000"));
        assertThat(settled.status()).isEqualTo("SETTLED");
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("0");

        // 不允许超额还款。
        assertThatThrownBy(() -> drawdownService.repay("DD-002", new BigDecimal("1.0000")))
                .isInstanceOf(ConflictException.class);
        // 未拨付不允许还款。
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-003", facilityId, snap.id(), new BigDecimal("10.0000")));
        drawdownService.approve("DD-003");
        assertThatThrownBy(() -> drawdownService.repay("DD-003", new BigDecimal("10.0000")))
                .isInstanceOf(ConflictException.class);

        // 台账：批准 + 两笔还款（取消未发生，拨付不产生额度流水）。
        List<LedgerEntryView> ledger = drawdownService.ledger(facilityId);
        assertThat(ledger).extracting(LedgerEntryView::type)
                .containsExactly("DRAWDOWN_APPROVED", "REPAYMENT", "REPAYMENT",
                        "DRAWDOWN_APPROVED");
    }

    // ------------------------------------------------------- 契约/有效期/额度拒绝

    @Test
    void covenant_breach_rejects_without_consuming_limit() {
        SnapshotInfo snap = breachSnapshot(LocalDate.of(2026, 3, 31));
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-BREACH", facilityId, snap.id(), new BigDecimal("100.0000")));

        DrawdownView view = drawdownService.approve("DD-BREACH");
        assertThat(view.status()).isEqualTo("REJECTED");
        assertThat(view.rejectionReason()).contains("CURRENT_RATIO");
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("0");
        assertThat(drawdownService.ledger(facilityId)).isEmpty();

        // 拒绝是终态：再次批准仍返回 REJECTED，不产生任何变化。
        assertThat(drawdownService.approve("DD-BREACH").status()).isEqualTo("REJECTED");
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void drawdown_outside_validity_is_rejected() {
        clock.advance(Duration.ofDays(200)); // 2027-01 附近，超出 2026-12-31
        SnapshotInfo snap = healthySnapshot(LocalDate.of(2026, 9, 30));
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-EXPIRED", facilityId, snap.id(), new BigDecimal("10.0000")));

        assertThat(drawdownService.approve("DD-EXPIRED").status()).isEqualTo("REJECTED");
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void drawdown_over_total_limit_is_rejected_but_consumed_limit_shortage_conflicts() {
        // 单笔超过总额度：业务拒绝。
        SnapshotInfo snap = healthySnapshot(LocalDate.of(2026, 3, 31));
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-HUGE", facilityId, snap.id(), new BigDecimal("5000.0000")));
        assertThat(drawdownService.approve("DD-HUGE").status()).isEqualTo("REJECTED");

        // 先占用 800，再申请 300（可用 200）：并发占用语义 → 冲突，仍 PENDING。
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-A", facilityId, snap.id(), new BigDecimal("800.0000")));
        drawdownService.approve("DD-A");
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-B", facilityId, snap.id(), new BigDecimal("300.0000")));
        assertThatThrownBy(() -> drawdownService.approve("DD-B"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("剩余额度不足");
        assertThat(drawdownService.getByBusinessNo("DD-B").status()).isEqualTo("PENDING");

        // 取消 DD-A 释放额度后，DD-B 可在同一旧申请上直接批准成功。
        drawdownService.cancel("DD-A");
        DrawdownView approvedB = drawdownService.approve("DD-B");
        assertThat(approvedB.status()).isEqualTo("APPROVED");
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("300.0000");
    }

    // ------------------------------------------------------- 冻结 / 快照更正冲突

    @Test
    void approval_against_frozen_facility_conflicts() {
        SnapshotInfo snap = healthySnapshot(LocalDate.of(2026, 3, 31));
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-FROZEN", facilityId, snap.id(), new BigDecimal("100.0000")));
        facilityService.freeze(facilityId);

        assertThatThrownBy(() -> drawdownService.approve("DD-FROZEN"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("冻结");
        // 冲突后申请保持 PENDING、额度不变；解冻后可正常批准。
        assertThat(drawdownService.getByBusinessNo("DD-FROZEN").status()).isEqualTo("PENDING");
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("0");

        facilityService.unfreeze(facilityId);
        assertThat(drawdownService.approve("DD-FROZEN").status()).isEqualTo("APPROVED");
    }

    @Test
    void approval_bound_to_corrected_snapshot_conflicts() {
        LocalDate period = LocalDate.of(2026, 3, 31);
        SnapshotInfo v1 = healthySnapshot(period);
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-OLD", facilityId, v1.id(), new BigDecimal("100.0000")));

        // 报告期快照被更正：新版本追加，旧版本 SUPERSEDED。
        SnapshotInfo v2 = snapshotService.submit(new SnapshotService.SubmitCommand(
                facilityId, period,
                Map.of("CURRENT_RATIO", new BigDecimal("1.90"),
                        "DEBT_TO_EBITDA", new BigDecimal("2.10"))));
        assertThat(v2.versionNo()).isEqualTo(2);

        assertThatThrownBy(() -> drawdownService.approve("DD-OLD"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("更正");
        assertThat(drawdownService.getByBusinessNo("DD-OLD").status()).isEqualTo("PENDING");
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("0");

        // 用新快照重新申请才能批准。
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-NEW", facilityId, v2.id(), new BigDecimal("100.0000")));
        assertThat(drawdownService.approve("DD-NEW").status()).isEqualTo("APPROVED");
    }

    // ------------------------------------------------------- 幂等

    @Test
    void submit_and_approve_are_idempotent_by_business_no() {
        SnapshotInfo snap = healthySnapshot(LocalDate.of(2026, 3, 31));
        var cmd = new DrawdownService.SubmitDrawdownCommand(
                "DD-IDEM", facilityId, snap.id(), new BigDecimal("100.0000"));
        var first = drawdownService.submit(cmd);
        assertThat(first.created()).isTrue();
        // 同业务号即使金额/快照不同也返回原申请。
        var repeat = drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-IDEM", facilityId, snap.id(), new BigDecimal("999.0000")));
        assertThat(repeat.created()).isFalse();
        assertThat(repeat.view().amount()).isEqualByComparingTo("100.0000");

        drawdownService.approve("DD-IDEM");
        DrawdownView again = drawdownService.approve("DD-IDEM");
        assertThat(again.status()).isEqualTo("APPROVED");
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("100.0000");
        assertThat(drawdownService.ledger(facilityId)).hasSize(1);
    }

    // ------------------------------------------------------- 契约判断查询

    @Test
    void covenant_check_query_reports_each_metric() {
        SnapshotInfo snap = healthySnapshot(LocalDate.of(2026, 3, 31));
        CovenantEvaluationView view = snapshotService.evaluate(snap.id());
        assertThat(view.allSatisfied()).isTrue();
        assertThat(view.checks()).hasSize(2);
        assertThat(view.checks()).extracting(c -> c.operator())
                .containsExactly("GTE", "LTE");
    }

    @Test
    void snapshot_of_other_facility_is_rejected() {
        Long other = facilityService.create(
                facility(new BigDecimal("100.00"), LocalDate.of(2026, 12, 31))).facilityId();
        SnapshotInfo snap = healthySnapshot(LocalDate.of(2026, 3, 31));
        assertThatThrownBy(() -> drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "DD-X", other, snap.id(), new BigDecimal("1.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于");
    }
}
