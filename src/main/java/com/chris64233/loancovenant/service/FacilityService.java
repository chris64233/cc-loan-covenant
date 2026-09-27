package com.chris64233.loancovenant.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.loancovenant.api.dto.CovenantSpec;
import com.chris64233.loancovenant.api.dto.CreateFacilityRequest;
import com.chris64233.loancovenant.api.dto.FacilityView;
import com.chris64233.loancovenant.domain.CreditFacility;
import com.chris64233.loancovenant.domain.FacilityStatus;
import com.chris64233.loancovenant.domain.FinancialCovenant;
import com.chris64233.loancovenant.repo.CreditFacilityRepository;
import com.chris64233.loancovenant.repo.FinancialCovenantRepository;

@Service
public class FacilityService {

    private final CreditFacilityRepository facilityRepository;
    private final FinancialCovenantRepository covenantRepository;

    public FacilityService(CreditFacilityRepository facilityRepository,
                           FinancialCovenantRepository covenantRepository) {
        this.facilityRepository = facilityRepository;
        this.covenantRepository = covenantRepository;
    }

    @Transactional
    public FacilityView create(CreateFacilityRequest request) {
        if (request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new ValidationException("INVALID_DATE_RANGE", "有效期止日不能早于起日");
        }
        CreditFacility facility = new CreditFacility(request.customerName(),
                request.currency().toUpperCase(java.util.Locale.ROOT),
                request.totalLimit(), request.effectiveFrom(), request.effectiveTo());
        facility = facilityRepository.save(facility);
        for (CovenantSpec spec : request.covenants()) {
            facility.getCovenants().add(new FinancialCovenant(facility, spec.metricCode(),
                    spec.displayName(), spec.operator(), spec.threshold()));
        }
        facility = facilityRepository.saveAndFlush(facility);
        return Views.toFacilityView(facility, List.copyOf(facility.getCovenants()));
    }

    @Transactional(readOnly = true)
    public FacilityView get(Long facilityId) {
        CreditFacility facility = requireFacility(facilityId);
        return Views.toFacilityView(facility,
                covenantRepository.findByFacilityIdOrderByIdAsc(facilityId));
    }

    @Transactional(readOnly = true)
    public List<FacilityView> list() {
        return facilityRepository.findAllByOrderByIdAsc().stream()
                .map(f -> Views.toFacilityView(f,
                        covenantRepository.findByFacilityIdOrderByIdAsc(f.getId())))
                .toList();
    }

    /** 冻结授信：冻结后存续提款仍可拨付/还款，但新批准一律冲突。 */
    @Transactional
    public FacilityView freeze(Long facilityId) {
        CreditFacility facility = requireFacility(facilityId);
        facility.setStatus(FacilityStatus.FROZEN);
        return Views.toFacilityView(facilityRepository.saveAndFlush(facility),
                covenantRepository.findByFacilityIdOrderByIdAsc(facilityId));
    }

    /** 解除冻结，恢复为 ACTIVE。 */
    @Transactional
    public FacilityView unfreeze(Long facilityId) {
        CreditFacility facility = requireFacility(facilityId);
        if (facility.getStatus() == FacilityStatus.CLOSED) {
            throw new ConflictException("FACILITY_CLOSED", "已关闭的授信不能恢复");
        }
        facility.setStatus(FacilityStatus.ACTIVE);
        return Views.toFacilityView(facilityRepository.saveAndFlush(facility),
                covenantRepository.findByFacilityIdOrderByIdAsc(facilityId));
    }

    @Transactional
    public FacilityView close(Long facilityId) {
        CreditFacility facility = requireFacility(facilityId);
        facility.setStatus(FacilityStatus.CLOSED);
        return Views.toFacilityView(facilityRepository.saveAndFlush(facility),
                covenantRepository.findByFacilityIdOrderByIdAsc(facilityId));
    }

    private CreditFacility requireFacility(Long facilityId) {
        return facilityRepository.findById(facilityId)
                .orElseThrow(() -> new NotFoundException("授信不存在: " + facilityId));
    }
}
