package com.chris64233.loancovenant.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.loancovenant.domain.FinancialCovenant;

public interface FinancialCovenantRepository extends JpaRepository<FinancialCovenant, Long> {

    List<FinancialCovenant> findByFacilityIdOrderByIdAsc(Long facilityId);
}
