package com.chris64233.loancovenant.repo;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.loancovenant.domain.FinancialSnapshot;
import com.chris64233.loancovenant.domain.SnapshotStatus;

public interface FinancialSnapshotRepository extends JpaRepository<FinancialSnapshot, Long> {

    List<FinancialSnapshot> findByFacilityIdOrderByReportPeriodAscVersionAsc(Long facilityId);

    List<FinancialSnapshot> findByFacilityIdAndReportPeriodOrderByVersionAsc(Long facilityId,
                                                                             java.time.LocalDate reportPeriod);

    Optional<FinancialSnapshot> findByFacilityIdAndReportPeriodAndVersion(Long facilityId,
                                                                          java.time.LocalDate reportPeriod,
                                                                          int version);

    Optional<FinancialSnapshot> findFirstByFacilityIdAndReportPeriodAndStatusOrderByVersionDesc(
            Long facilityId, java.time.LocalDate reportPeriod, SnapshotStatus status);

    /** 追加快照版本时锁定报告期分组，避免并发产生相同版本号。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FinancialSnapshot s where s.facility.id = :facilityId "
            + "and s.reportPeriod = :reportPeriod order by s.version asc")
    List<FinancialSnapshot> lockByFacilityAndPeriod(@Param("facilityId") Long facilityId,
                                                    @Param("reportPeriod") java.time.LocalDate reportPeriod);
}
