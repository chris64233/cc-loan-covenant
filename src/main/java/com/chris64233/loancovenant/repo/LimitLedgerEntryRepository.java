package com.chris64233.loancovenant.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.loancovenant.domain.LimitLedgerEntry;

public interface LimitLedgerEntryRepository extends JpaRepository<LimitLedgerEntry, Long> {

    List<LimitLedgerEntry> findByFacilityIdOrderByIdAsc(Long facilityId);
}
