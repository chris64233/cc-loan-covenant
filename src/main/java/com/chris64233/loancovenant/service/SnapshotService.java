package com.chris64233.loancovenant.service;

import com.chris64233.loancovenant.domain.CreditFacility;
import com.chris64233.loancovenant.domain.FinancialSnapshot;
import com.chris64233.loancovenant.domain.SnapshotStatus;
import com.chris64233.loancovenant.repo.FinancialSnapshotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 财务快照服务：同一报告期新版本只能追加，旧版本转 SUPERSEDED 但永不删除。
 *
 * <p>追加与提款批准都先锁授信行，再读/改快照（统一加锁顺序），
 * 因此“批准进行中被更正”与“更正后基于旧版本批准”二者严格串行，
 * 后者会在批准时检测到 SUPERSEDED 并返回冲突。
 */
@Service
public class SnapshotService {

    private final FinancialSnapshotRepository snapshotRepository;
    private final FacilityService facilityService;
    private final CovenantEvaluator evaluator;
    private final Clock clock;

    public SnapshotService(FinancialSnapshotRepository snapshotRepository,
                           FacilityService facilityService,
                           CovenantEvaluator evaluator, Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.facilityService = facilityService;
        this.evaluator = evaluator;
        this.clock = clock;
    }

    /** 提交快照命令。 */
    public record SubmitCommand(Long facilityId, LocalDate reportingPeriod,
                                Map<String, BigDecimal> metrics) {
    }

    @Transactional
    public SnapshotInfo submit(SubmitCommand cmd) {
        if (cmd.reportingPeriod() == null) {
            throw new IllegalArgumentException("报告期不能为空");
        }
        if (cmd.metrics() == null || cmd.metrics().isEmpty()) {
            throw new IllegalArgumentException("至少提交一个财务指标");
        }
        // 锁授信行：串行化同一报告期的版本号分配，并与批准事务互斥。
        CreditFacility facility = facilityService.requireLocked(cmd.facilityId());

        List<FinancialSnapshot> prior =
                snapshotRepository.findByFacilityIdAndReportingPeriodOrderByVersionNoDesc(
                        facility.getId(), cmd.reportingPeriod());
        int nextVersion = prior.stream().mapToInt(FinancialSnapshot::getVersionNo).max().orElse(0) + 1;

        FinancialSnapshot snapshot = new FinancialSnapshot(
                facility, cmd.reportingPeriod(), nextVersion,
                Map.copyOf(cmd.metrics()), Instant.now(clock));
        snapshot = snapshotRepository.save(snapshot);

        // 旧版本只追加不覆盖：标记取代，实体保留。
        // 此处已持有授信行锁；提交（授信行→快照行）与批准（授信行→提款行→快照行）
        // 均以授信行为第一把锁，按实体 ID 顺序加锁，不会死锁。
        for (FinancialSnapshot old : prior) {
            if (old.getStatus() == SnapshotStatus.CURRENT) {
                old.markSuperseded();
            }
        }
        return toInfo(snapshot);
    }

    @Transactional(readOnly = true)
    public List<SnapshotInfo> listByFacility(Long facilityId) {
        facilityService.require(facilityId);
        return snapshotRepository.findByFacilityIdOrderByReportingPeriodDescVersionNoDesc(facilityId)
                .stream().map(this::toInfo).toList();
    }

    /** 契约判断查询：给定快照版本（含已被取代的历史版本）逐项评估。 */
    @Transactional(readOnly = true)
    public CovenantEvaluationView evaluate(Long snapshotId) {
        FinancialSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new NotFoundException("财务快照不存在: " + snapshotId));
        List<CovenantCheck> checks = evaluator.evaluate(
                snapshot.getFacility().getCovenants(), snapshot);
        return new CovenantEvaluationView(
                snapshot.getFacility().getId(),
                snapshot.getId(),
                snapshot.getReportingPeriod(),
                snapshot.getVersionNo(),
                snapshot.getStatus().name(),
                checks.stream().allMatch(CovenantCheck::satisfied),
                checks);
    }

    SnapshotInfo toInfo(FinancialSnapshot s) {
        return new SnapshotInfo(
                s.getId(), s.getFacility().getId(), s.getReportingPeriod(),
                s.getVersionNo(), s.getStatus().name(), Map.copyOf(s.getMetrics()),
                s.getSubmittedAt());
    }
}
