package com.chris64233.loancovenant.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.loancovenant.api.dto.CovenantCheckItem;
import com.chris64233.loancovenant.api.dto.CovenantCheckView;
import com.chris64233.loancovenant.api.dto.SnapshotView;
import com.chris64233.loancovenant.api.dto.SubmitSnapshotRequest;
import com.chris64233.loancovenant.domain.CreditFacility;
import com.chris64233.loancovenant.domain.FinancialCovenant;
import com.chris64233.loancovenant.domain.FinancialSnapshot;
import com.chris64233.loancovenant.repo.CreditFacilityRepository;
import com.chris64233.loancovenant.repo.FinancialCovenantRepository;
import com.chris64233.loancovenant.repo.FinancialSnapshotRepository;

@Service
public class SnapshotService {

    private final FinancialSnapshotRepository snapshotRepository;
    private final FinancialCovenantRepository covenantRepository;
    private final CreditFacilityRepository facilityRepository;
    private final Clock clock;

    public SnapshotService(FinancialSnapshotRepository snapshotRepository,
                           FinancialCovenantRepository covenantRepository,
                           CreditFacilityRepository facilityRepository, Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.covenantRepository = covenantRepository;
        this.facilityRepository = facilityRepository;
        this.clock = clock;
    }

    /**
     * 提交快照。同一报告期再次提交只会追加新版本：旧版本标记为 SUPERSEDED 但绝不覆盖。
     * 对授信行和该报告期分组加锁，与提款批准互斥并防止并发产生相同版本号。
     */
    @Transactional
    public SnapshotView submit(Long facilityId, SubmitSnapshotRequest request) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new NotFoundException("授信不存在: " + facilityId);
        }
        // 锁授信行：与提款批准互斥；锁报告期分组避免并发产生相同版本号。
        CreditFacility facility = facilityRepository.lockById(facilityId);
        List<FinancialSnapshot> periodSnapshots =
                snapshotRepository.lockByFacilityAndPeriod(facilityId, request.reportPeriod());
        int nextVersion = periodSnapshots.stream()
                .mapToInt(FinancialSnapshot::getVersion).max().orElse(0) + 1;
        for (FinancialSnapshot s : periodSnapshots) {
            s.markSuperseded();
        }
        FinancialSnapshot snapshot = new FinancialSnapshot(facility, request.reportPeriod(),
                nextVersion, request.metrics());
        snapshot = snapshotRepository.saveAndFlush(snapshot);
        return Views.toSnapshotView(snapshot);
    }

    @Transactional(readOnly = true)
    public List<SnapshotView> list(Long facilityId) {
        requireFacility(facilityId);
        return snapshotRepository
                .findByFacilityIdOrderByReportPeriodAscVersionAsc(facilityId).stream()
                .map(Views::toSnapshotView).toList();
    }

    @Transactional(readOnly = true)
    public List<SnapshotView> listPeriod(Long facilityId, LocalDate reportPeriod) {
        requireFacility(facilityId);
        return snapshotRepository
                .findByFacilityIdAndReportPeriodOrderByVersionAsc(facilityId, reportPeriod)
                .stream().map(Views::toSnapshotView).toList();
    }

    @Transactional(readOnly = true)
    public SnapshotView get(Long facilityId, Long snapshotId) {
        return Views.toSnapshotView(requireSnapshot(facilityId, snapshotId));
    }

    /**
     * 契约判断：对指定快照版本逐条评估授信契约，并给出该快照是否仍为当前版本、
     * 授信在评估日是否有效。
     */
    @Transactional(readOnly = true)
    public CovenantCheckView check(Long facilityId, Long snapshotId, LocalDate evaluatedOn) {
        CreditFacility facility = requireFacility(facilityId);
        FinancialSnapshot snapshot = requireSnapshot(facilityId, snapshotId);
        LocalDate date = evaluatedOn != null ? evaluatedOn : LocalDate.now(clock);

        List<FinancialCovenant> covenants =
                covenantRepository.findByFacilityIdOrderByIdAsc(facilityId);
        List<CovenantCheckItem> items = new ArrayList<>();
        boolean allSatisfied = true;
        for (FinancialCovenant c : covenants) {
            var value = snapshot.metricValue(c.getMetricCode());
            boolean satisfied = c.isSatisfiedBy(value);
            allSatisfied &= satisfied;
            items.add(Views.toCheckItem(c, value, satisfied));
        }
        return new CovenantCheckView(facilityId, snapshotId, snapshot.getReportPeriod(),
                snapshot.getVersion(), Views.isCurrent(snapshot),
                allSatisfied && !covenants.isEmpty(), facility.isEffectiveOn(date), date, items);
    }

    private CreditFacility requireFacility(Long facilityId) {
        return facilityRepository.findById(facilityId)
                .orElseThrow(() -> new NotFoundException("授信不存在: " + facilityId));
    }

    private FinancialSnapshot requireSnapshot(Long facilityId, Long snapshotId) {
        FinancialSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new NotFoundException("财务快照不存在: " + snapshotId));
        if (!snapshot.getFacility().getId().equals(facilityId)) {
            throw new NotFoundException("财务快照不存在: " + snapshotId);
        }
        return snapshot;
    }
}
