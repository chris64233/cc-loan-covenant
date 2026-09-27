package com.chris64233.loancovenant.repo;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.loancovenant.domain.CreditFacility;

public interface CreditFacilityRepository extends JpaRepository<CreditFacility, Long> {

    /** 悲观写锁：批准/取消/还款串行化同一授信上的额度变化。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from CreditFacility f where f.id = :id")
    CreditFacility lockById(@Param("id") Long id);

    List<CreditFacility> findAllByOrderByIdAsc();
}
