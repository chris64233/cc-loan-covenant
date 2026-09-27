package com.chris64233.loancovenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import com.chris64233.loancovenant.api.dto.DrawdownView;
import com.chris64233.loancovenant.api.dto.FacilityView;
import com.chris64233.loancovenant.api.dto.LedgerEntryView;
import com.chris64233.loancovenant.api.dto.RepaymentRequest;
import com.chris64233.loancovenant.api.dto.SnapshotView;
import com.chris64233.loancovenant.api.dto.SubmitDrawdownRequest;
import com.chris64233.loancovenant.api.dto.SubmitSnapshotRequest;
import com.chris64233.loancovenant.domain.DrawdownStatus;
import com.chris64233.loancovenant.domain.LedgerEntryType;
import com.chris64233.loancovenant.service.DrawdownRejectedException;
import com.chris64233.loancovenant.service.DrawdownService;
import com.chris64233.loancovenant.service.ConflictException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrawdownLifecycleTest extends AbstractIntegrationTest {

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
    void happyPath_approveDisburseRepayAndLedger() {
        DrawdownView d = submit("DD-001", "3000000");
        DrawdownView approved = drawdownService.approve(facility.id(), "DD-001");
        assertThat(approved.status()).isEqualTo(DrawdownStatus.APPROVED);
        assertThat(approved.approvalEvidence()).contains("\"covenants\"")
                .contains("\"usedAfter\":3000000.0000");

        FacilityView after = facilityService.get(facility.id());
        assertThat(after.usedLimit()).isEqualByComparingTo("3000000");
        assertThat(after.availableLimit()).isEqualByComparingTo("7000000");

        // 拨付不改变额度。
        DrawdownView disbursed = drawdownService.disburse(facility.id(), "DD-001");
        assertThat(disbursed.status()).isEqualTo(DrawdownStatus.DISBURSED);
        assertThat(facilityService.get(facility.id()).usedLimit())
                .isEqualByComparingTo("3000000");

        // 部分还款释放已用额度。
        DrawdownView partial = drawdownService.repay(facility.id(), "DD-001",
                new RepaymentRequest(new BigDecimal("1000000")));
        assertThat(partial.status()).isEqualTo(DrawdownStatus.DISBURSED);
        assertThat(partial.repaidAmount()).isEqualByComparingTo("1000000");
        assertThat(partial.outstandingAmount()).isEqualByComparingTo("2000000");
        assertThat(facilityService.get(facility.id()).usedLimit())
                .isEqualByComparingTo("2000000");

        // 还清后 REPAID。
        DrawdownView repaid = drawdownService.repay(facility.id(), "DD-001",
                new RepaymentRequest(new BigDecimal("2000000")));
        assertThat(repaid.status()).isEqualTo(DrawdownStatus.REPAID);
        assertThat(facilityService.get(facility.id()).usedLimit()).isEqualByComparingTo("0");

        List<LedgerEntryView> ledger = drawdownService.ledger(facility.id());
        assertThat(ledger).hasSize(3);
        assertThat(ledger).extracting(LedgerEntryView::entryType).containsExactly(
                LedgerEntryType.DRAWDOWN_APPROVED,
                LedgerEntryType.REPAYMENT,
                LedgerEntryType.REPAYMENT);
        assertThat(ledger).extracting(LedgerEntryView::usedAfter)
                .map(Object::toString).containsExactly("3000000.0000", "2000000.0000", "0.0000");
    }

    @Test
    void approveIsIdempotentForSameBusinessNo() {
        submit("DD-IDEM", "1000");
        DrawdownView first = drawdownService.approve(facility.id(), "DD-IDEM");
        DrawdownView second = drawdownService.approve(facility.id(), "DD-IDEM");
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(facilityService.get(facility.id()).usedLimit()).isEqualByComparingTo("1000");
        assertThat(drawdownService.ledger(facility.id())).hasSize(1);
    }

    @Test
    void submitIsIdempotentAndDoesNotDuplicate() {
        submit("DD-SUB", "1000");
        DrawdownView again = drawdownService.submit(facility.id(),
                new SubmitDrawdownRequest("DD-SUB", snapshot.id(), new BigDecimal("9999")));
        assertThat(again.amount()).isEqualByComparingTo("1000");
        assertThat(drawdownService.list(facility.id())).hasSize(1);
    }

    @Test
    void rejectsWhenCovenantBreached() {
        SnapshotView bad = unhealthySnapshot(facility.id(), period);
        drawdownService.submit(facility.id(),
                new SubmitDrawdownRequest("DD-BAD", bad.id(), new BigDecimal("1000")));
        assertThatThrownBy(() -> drawdownService.approve(facility.id(), "DD-BAD"))
                .isInstanceOf(DrawdownRejectedException.class)
                .hasMessageContaining("财务契约不满足");
        // 没有任何额度被占用，也没有台账。
        assertThat(facilityService.get(facility.id()).usedLimit()).isEqualByComparingTo("0");
        assertThat(drawdownService.ledger(facility.id())).isEmpty();
    }

    @Test
    void rejectsWhenFacilityExpired() {
        FacilityView expired = createFacility(new BigDecimal("10000000"),
                TODAY.minusDays(60), TODAY.minusDays(1),
                standardFacility().covenants().stream()
                        .map(c -> new com.chris64233.loancovenant.api.dto.CovenantSpec(
                                c.metricCode(), c.displayName(), c.operator(), c.threshold()))
                        .toList());
        SnapshotView s = healthySnapshot(expired.id(), period);
        drawdownService.submit(expired.id(),
                new SubmitDrawdownRequest("DD-EXP", s.id(), new BigDecimal("1000")));
        assertThatThrownBy(() -> drawdownService.approve(expired.id(), "DD-EXP"))
                .isInstanceOf(DrawdownRejectedException.class)
                .hasMessageContaining("有效期");
    }

    @Test
    void rejectsWhenRemainingLimitInsufficient() {
        submit("DD-BIG", "10000001");
        assertThatThrownBy(() -> drawdownService.approve(facility.id(), "DD-BIG"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("剩余额度不足");
        assertThat(drawdownService.ledger(facility.id())).isEmpty();
    }

    @Test
    void cancelApprovedDrawdownReleasesLimit() {
        submit("DD-CXL", "4000000");
        drawdownService.approve(facility.id(), "DD-CXL");
        assertThat(facilityService.get(facility.id()).usedLimit()).isEqualByComparingTo("4000000");

        DrawdownView cancelled = drawdownService.cancel(facility.id(), "DD-CXL");
        assertThat(cancelled.status()).isEqualTo(DrawdownStatus.CANCELLED);
        assertThat(facilityService.get(facility.id()).usedLimit()).isEqualByComparingTo("0");
        assertThat(facilityService.get(facility.id()).availableLimit())
                .isEqualByComparingTo("10000000");

        List<LedgerEntryView> ledger = drawdownService.ledger(facility.id());
        assertThat(ledger).extracting(LedgerEntryView::entryType)
                .containsExactly(LedgerEntryType.DRAWDOWN_APPROVED,
                        LedgerEntryType.DRAWDOWN_CANCELLED);
        assertThat(ledger.get(1).deltaUsed()).isEqualByComparingTo("-4000000");
    }

    @Test
    void cancelIsIdempotentAndDisbursedCannotCancel() {
        submit("DD-D", "1000");
        drawdownService.approve(facility.id(), "DD-D");
        drawdownService.disburse(facility.id(), "DD-D");
        assertThatThrownBy(() -> drawdownService.cancel(facility.id(), "DD-D"))
                .isInstanceOf(ConflictException.class);
        // 额度仍占用。
        assertThat(facilityService.get(facility.id()).usedLimit()).isEqualByComparingTo("1000");
    }

    @Test
    void pendingCannotCancel() {
        submit("DD-P", "1000");
        assertThatThrownBy(() -> drawdownService.cancel(facility.id(), "DD-P"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void repaymentCannotExceedOutstanding() {
        submit("DD-R", "1000");
        drawdownService.approve(facility.id(), "DD-R");
        drawdownService.disburse(facility.id(), "DD-R");
        assertThatThrownBy(() -> drawdownService.repay(facility.id(), "DD-R",
                new RepaymentRequest(new BigDecimal("1001"))))
                .isInstanceOf(com.chris64233.loancovenant.service.ValidationException.class)
                .hasMessageContaining("超过未还本金");
        assertThat(facilityService.get(facility.id()).usedLimit()).isEqualByComparingTo("1000");
    }

    @Test
    void submitBindsExplicitVersionButApproveFailsAfterCorrection() {
        SnapshotView v1 = healthySnapshot(facility.id(), period);
        // 提交新版本更正报告期。
        snapshotService.submit(facility.id(), new SubmitSnapshotRequest(period,
                java.util.Map.of("debtRatio", new BigDecimal("0.50"),
                        "currentRatio", new BigDecimal("2.00"))));
        // 仍可绑定明确的历史版本提交；批准时因快照已被更正而冲突。
        DrawdownView bound = drawdownService.submit(facility.id(),
                new SubmitDrawdownRequest("DD-OLD", v1.id(), new BigDecimal("1000")));
        assertThat(bound.snapshotId()).isEqualTo(v1.id());
        assertThatThrownBy(() -> drawdownService.approve(facility.id(), "DD-OLD"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("被更正");
        assertThat(drawdownService.ledger(facility.id())).isEmpty();
    }

    private DrawdownView submit(String businessNo, String amount) {
        return drawdownService.submit(facility.id(),
                new SubmitDrawdownRequest(businessNo, snapshot.id(), new BigDecimal(amount)));
    }
}
