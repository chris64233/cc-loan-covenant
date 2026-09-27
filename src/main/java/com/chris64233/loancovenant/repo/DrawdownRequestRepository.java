package com.chris64233.loancovenant.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.loancovenant.domain.DrawdownRequest;
import com.chris64233.loancovenant.domain.DrawdownStatus;

public interface DrawdownRequestRepository extends JpaRepository<DrawdownRequest, Long> {

    Optional<DrawdownRequest> findByBusinessNo(String businessNo);

    boolean existsByBusinessNo(String businessNo);

    List<DrawdownRequest> findByFacilityIdOrderByIdAsc(Long facilityId);

    List<DrawdownRequest> findByFacilityIdAndStatusOrderByIdAsc(Long facilityId, DrawdownStatus status);
}
