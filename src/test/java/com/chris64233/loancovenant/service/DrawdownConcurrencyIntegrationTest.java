package com.chris64233.loancovenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.loancovenant.domain.CovenantOperator;
import com.chris64233.loancovenant.testsupport.MutableClock;
import com.chris64233.loancovenant.testsupport.TestClockConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 并发语义测试：悲观行锁串行化同一授信的额度变动。
 */
@SpringBootTest
@Import(TestClockConfig.class)
class DrawdownConcurrencyIntegrationTest {

    @Autowired FacilityService facilityService;
    @Autowired SnapshotService snapshotService;
    @Autowired DrawdownService drawdownService;
    @Autowired MutableClock clock;

    private Long facilityId;
    private SnapshotInfo snapshot;

    @BeforeEach
    void setUp() {
        clock.setInstant(java.time.Instant.parse("2026-06-30T10:00:00Z"));
        facilityId = facilityService.create(new CreateFacilityCommand(
                "并发企业", "CNY", new BigDecimal("1000.0000"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                List.of(new CreateFacilityCommand.CovenantSpec(
                        "CURRENT_RATIO", "流动比率", CovenantOperator.GTE,
                        new BigDecimal("1.50"))))).facilityId();
        snapshot = snapshotService.submit(new SnapshotService.SubmitCommand(
                facilityId, LocalDate.of(2026, 3, 31),
                Map.of("CURRENT_RATIO", new BigDecimal("2.00"))));
    }

    @Test
    void concurrent_approvals_never_breach_total_limit() throws Exception {
        int threads = 10;
        BigDecimal each = new BigDecimal("150.0000"); // 总额 1500 > 额度 1000
        for (int i = 0; i < threads; i++) {
            drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                    "CONC-DD-" + i, facilityId, snapshot.id(), each));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        Map<String, String> outcomes = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String no = "CONC-DD-" + i;
                futures.add(pool.submit(() -> {
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        DrawdownView v = drawdownService.approve(no);
                        outcomes.put(no, v.status());
                    } catch (ConflictException e) {
                        outcomes.put(no, "CONFLICT:" + e.getCode());
                    } catch (Exception e) {
                        outcomes.put(no, "ERROR:" + e.getClass().getSimpleName());
                    } finally {
                        done.countDown();
                    }
                }));
            }
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        long approved = outcomes.values().stream().filter("APPROVED"::equals).count();
        long conflicted = outcomes.values().stream().filter(v -> v.startsWith("CONFLICT")).count();
        assertThat(approved).isEqualTo(6);          // 6 * 150 = 900
        assertThat(approved + conflicted).isEqualTo(10);
        assertThat(outcomes.values()).noneMatch(v -> v.startsWith("ERROR"));

        FacilityBalanceView balance = facilityService.balance(facilityId);
        assertThat(balance.usedAmount()).isEqualByComparingTo("900.0000");
        assertThat(balance.usedAmount()).isLessThanOrEqualTo(balance.totalLimit());

        // 失败者仍是 PENDING；取消一笔释放 150 后，申请 150 可继续批准。
        String pendingNo = outcomes.entrySet().stream()
                .filter(e -> e.getValue().startsWith("CONFLICT"))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        assertThat(drawdownService.getByBusinessNo(pendingNo).status()).isEqualTo("PENDING");
        assertThatThrownBy(() -> drawdownService.approve(pendingNo))
                .isInstanceOf(ConflictException.class); // 余量 100，仍不足 150

        String approvedNo = outcomes.entrySet().stream()
                .filter(e -> "APPROVED".equals(e.getValue()))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        drawdownService.cancel(approvedNo);
        assertThat(drawdownService.approve(pendingNo).status()).isEqualTo("APPROVED");
        assertThat(facilityService.balance(facilityId).usedAmount())
                .isEqualByComparingTo("900.0000");
    }

    @Test
    void concurrent_approvals_of_the_same_drawdown_reserve_exactly_once() throws Exception {
        int threads = 8;
        drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                "SAME-DD", facilityId, snapshot.id(), new BigDecimal("100.0000")));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<String>> outcomes = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                outcomes.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        return drawdownService.approve("SAME-DD").status();
                    } catch (ConflictException e) {
                        return "CONFLICT";
                    }
                }));
            }
            List<String> results = new ArrayList<>();
            for (Future<String> f : outcomes) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).containsOnly("APPROVED");
        } finally {
            pool.shutdownNow();
        }

        // 只允许占用一次、只有一条台账。
        assertThat(facilityService.balance(facilityId).usedAmount())
                .isEqualByComparingTo("100.0000");
        assertThat(drawdownService.ledger(facilityId)).hasSize(1);
    }

    @Test
    void concurrent_submits_with_same_business_no_create_exactly_one() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<Boolean>> createdFlags = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                createdFlags.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return drawdownService.submit(new DrawdownService.SubmitDrawdownCommand(
                            "UNIQUE-NO", facilityId, snapshot.id(),
                            new BigDecimal("100.0000"))).created();
                }));
            }
            long created = 0;
            for (Future<Boolean> f : createdFlags) {
                if (f.get(30, TimeUnit.SECONDS)) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        // 并发提交不占用额度。
        assertThat(facilityService.balance(facilityId).usedAmount()).isEqualByComparingTo("0");
        assertThat(drawdownService.listByFacility(facilityId)).hasSize(1);
    }

    @Test
    void concurrent_approval_and_snapshot_correction_never_leaves_partial_state() throws Exception {
        DrawdownService.SubmitResult r = drawdownService.submit(
                new DrawdownService.SubmitDrawdownCommand(
                        "RACE-DD", facilityId, snapshot.id(), new BigDecimal("100.0000")));
        assertThat(r.created()).isTrue();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        List<String> results = new ArrayList<>();
        try {
            pool.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    String st = drawdownService.approve("RACE-DD").status();
                    synchronized (results) {
                        results.add("APPROVE:" + st);
                    }
                } catch (ConflictException e) {
                    synchronized (results) {
                        results.add("APPROVE:CONFLICT");
                    }
                } catch (Exception e) {
                    synchronized (results) {
                        results.add("APPROVE:ERROR");
                    }
                } finally {
                    done.countDown();
                }
            });
            pool.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    snapshotService.submit(new SnapshotService.SubmitCommand(
                            facilityId, LocalDate.of(2026, 3, 31),
                            Map.of("CURRENT_RATIO", new BigDecimal("2.20"))));
                    synchronized (results) {
                        results.add("CORRECT:OK");
                    }
                } catch (Exception e) {
                    synchronized (results) {
                        results.add("CORRECT:ERROR");
                    }
                } finally {
                    done.countDown();
                }
            });
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(results).anyMatch(r2 -> r2.equals("CORRECT:OK"));
        assertThat(results).noneMatch(r2 -> r2.endsWith("ERROR"));

        // 两种合法线性化之一：
        // A) 批准先于更正 → APPROVED，占用 100；
        // B) 更正先于批准 → CONFLICT，PENDING，不占额度。
        BigDecimal used = facilityService.balance(facilityId).usedAmount();
        String status = drawdownService.getByBusinessNo("RACE-DD").status();
        if ("APPROVED".equals(status)) {
            assertThat(used).isEqualByComparingTo("100.0000");
        } else {
            assertThat(status).isEqualTo("PENDING");
            assertThat(used).isEqualByComparingTo("0");
        }
    }
}
