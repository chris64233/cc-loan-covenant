package com.chris64233.loancovenant.service;

import com.chris64233.loancovenant.domain.CreditFacility;
import com.chris64233.loancovenant.domain.FinancialCovenant;
import com.chris64233.loancovenant.repo.CreditFacilityRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 授信建立、冻结与余额查询。 */
@Service
public class FacilityService {

    private final CreditFacilityRepository facilityRepository;
    private final Clock clock;

    public FacilityService(CreditFacilityRepository facilityRepository, Clock clock) {
        this.facilityRepository = facilityRepository;
        this.clock = clock;
    }

    @Transactional
    public FacilityBalanceView create(CreateFacilityCommand cmd) {
        LocalDate today = LocalDate.now(clock);
        if (cmd.endDate().isBefore(cmd.startDate())) {
            throw new IllegalArgumentException("授信结束日不能早于开始日");
        }
        if (cmd.endDate().isBefore(today)) {
            throw new IllegalArgumentException("授信有效期已过，结束日早于今天");
        }
        CreditFacility facility = new CreditFacility(
                cmd.customerName(), cmd.currency(), cmd.totalLimit(),
                cmd.startDate(), cmd.endDate());
        for (CreateFacilityCommand.CovenantSpec spec : cmd.covenants()) {
            facility.addCovenant(new FinancialCovenant(
                    spec.metricKey(), spec.displayName(), spec.operator(), spec.threshold()));
        }
        facility = facilityRepository.save(facility);
        return toBalanceView(facility, today);
    }

    @Transactional(readOnly = true)
    public FacilityBalanceView balance(Long id) {
        CreditFacility f = require(id);
        return toBalanceView(f, LocalDate.now(clock));
    }

    /** 审批期间冻结授信。 */
    @Transactional
    public void freeze(Long id) {
        requireLocked(id).freeze();
    }

    /** 解除冻结。 */
    @Transactional
    public void unfreeze(Long id) {
        requireLocked(id).unfreeze();
    }

    CreditFacility require(Long id) {
        return facilityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("授信不存在: " + id));
    }

    CreditFacility requireLocked(Long id) {
        return facilityRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("授信不存在: " + id));
    }

    private FacilityBalanceView toBalanceView(CreditFacility f, LocalDate today) {
        boolean within = !today.isBefore(f.getStartDate()) && !today.isAfter(f.getEndDate());
        List<CovenantInfo> covenants = f.getCovenants().stream()
                .map(c -> new CovenantInfo(c.getId(), c.getMetricKey(), c.getDisplayName(),
                        c.getOperator().name(), c.getThreshold()))
                .toList();
        return new FacilityBalanceView(
                f.getId(), f.getCustomerName(), f.getCurrency(),
                f.getTotalLimit(), f.getUsedAmount(), f.getAvailableAmount(),
                f.getStartDate(), f.getEndDate(), f.isFrozen(), within,
                f.getVersion(), covenants);
    }
}
