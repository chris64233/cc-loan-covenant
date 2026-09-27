package com.chris64233.loancovenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import com.chris64233.loancovenant.api.dto.DrawdownView;
import com.chris64233.loancovenant.api.dto.FacilityView;
import com.chris64233.loancovenant.api.dto.SnapshotView;
import com.chris64233.loancovenant.api.dto.RepaymentRequest;
import com.chris64233.loancovenant.api.dto.SubmitDrawdownRequest;
import com.chris64233.loancovenant.domain.DrawdownStatus;
import com.chris64233.loancovenant.service.BusinessRuleException;
import com.chris64233.loancovenant.service.ConflictException;
import com.chris64233.loancovenant.service.DrawdownService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发批准不得突破总额度：多线程同时批准时仅额度内的申请成功，其余冲突失败。
 */
class ConcurrentApprovalTest extends AbstractIntegrationTest {

    private final LocalDate period = LocalDate.of(2026, 6, 30);

    @Autowired
    private DrawdownService drawdownService;

    private FacilityView facility;

    @BeforeEach
    void setUp() {
        fixClock();
        facility = standardFacility(); // 1000 万
        SnapshotView snapshot = healthySnapshot(facility.id(), period);
        // 预先提交 10 笔各 300 万（合计 3000 万），让批准阶段发生额度竞争。
        for (int i = 1; i <= 10; i++) {
            drawdownService.submit(facility.id(), new SubmitDrawdownRequest(
                    "DD-C" + i, snapshot.id(), new BigDecimal("3000000")));
        }
    }

    @Test
    void concurrentApprovalsNeverExceedTotalLimit() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Outcome>> tasks = IntStream.rangeClosed(1, threads)
                    .<Callable<Outcome>>mapToObj(i -> () -> {
                        start.await();
                        try {
                            DrawdownView v = drawdownService.approve(facility.id(), "DD-C" + i);
                            return new Outcome(i, true, v.status() == DrawdownStatus.APPROVED);
                        } catch (ConflictException e) {
                            return new Outcome(i, false, false);
                        }
                    }).toList();
            List<Future<Outcome>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            List<Outcome> outcomes = new java.util.ArrayList<>();
            for (Future<Outcome> f : futures) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }

            long approved = outcomes.stream().filter(o -> o.success).count();
            // 3 笔恰好 900 万；第 4 笔 300 万会超过 1000 万，必须失败。
            assertThat(approved).isEqualTo(3);
            FacilityView after = facilityService.get(facility.id());
            assertThat(after.usedLimit()).isEqualByComparingTo("9000000");
            assertThat(after.availableLimit()).isEqualByComparingTo("1000000");
            assertThat(drawdownService.ledger(facility.id())).hasSize(3);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentRepaymentsNeverExceedPrincipal() throws Exception {
        // 新建一笔 100 万的已拨付提款。
        SnapshotView s = healthySnapshot(facility.id(), LocalDate.of(2025, 12, 31));
        drawdownService.submit(facility.id(),
                new SubmitDrawdownRequest("DD-RC", s.id(), new BigDecimal("1000000")));
        drawdownService.approve(facility.id(), "DD-RC");
        drawdownService.disburse(facility.id(), "DD-RC");

        // 两笔 80 万还款并发：只有一笔能成功，另一笔必须被拒绝。
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, threads).<Callable<Boolean>>mapToObj(
                    i -> () -> {
                        start.await();
                        try {
                            drawdownService.repay(facility.id(), "DD-RC",
                                    new RepaymentRequest(new BigDecimal("800000")));
                            return true;
                        } catch (BusinessRuleException e) {
                            return false;
                        }
                    }).toList();
            List<Future<Boolean>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            long succeeded = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            DrawdownView d = drawdownService.get(facility.id(), "DD-RC");
            assertThat(d.status()).isEqualTo(DrawdownStatus.DISBURSED);
            assertThat(d.repaidAmount()).isEqualByComparingTo("800000");
            assertThat(facilityService.get(facility.id()).usedLimit())
                    .isEqualByComparingTo("200000"); // 100 万提款，80 万还款后剩余 20 万
        } finally {
            pool.shutdownNow();
        }
    }

    private record Outcome(int index, boolean success, boolean approved) {
    }
}
