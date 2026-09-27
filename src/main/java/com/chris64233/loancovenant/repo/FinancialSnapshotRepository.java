package com.chris64233.loancovenant.repo;

import com.chris64233.loancovenant.domain.FinancialSnapshot;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialSnapshotRepository extends JpaRepository<FinancialSnapshot, Long> {

    List<FinancialSnapshot> findByFacilityIdAndReportingPeriodOrderByVersionNoDesc(
            Long facilityId, java.time.LocalDate reportingPeriod);

    List<FinancialSnapshot> findByFacilityIdOrderByReportingPeriodDescVersionNoDesc(Long facilityId);

    /**
     * 悲观锁加载快照：追加新版本标记旧版本 SUPERSEDED 时，
     * 与提款批准事务互斥，使基于旧状态的批准检测到更正并返回冲突。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FinancialSnapshot s where s.id = :id")
    Optional<FinancialSnapshot> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(max(s.versionNo), 0) from FinancialSnapshot s "
            + "where s.facility.id = :facilityId and s.reportingPeriod = :period")
    int findMaxVersionNo(@Param("facilityId") Long facilityId,
                         @Param("period") java.time.LocalDate period);
}
