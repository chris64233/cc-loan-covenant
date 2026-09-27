package com.chris64233.loancovenant.repo;

import com.chris64233.loancovenant.domain.CreditFacility;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CreditFacilityRepository extends JpaRepository<CreditFacility, Long> {

    /**
     * 行级悲观写锁：批准/取消/还款等额度变动事务串行化同一授信行，
     * 配合条件校验保证并发批准不突破总额度。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from CreditFacility f where f.id = :id")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    Optional<CreditFacility> findByIdForUpdate(@Param("id") Long id);
}
