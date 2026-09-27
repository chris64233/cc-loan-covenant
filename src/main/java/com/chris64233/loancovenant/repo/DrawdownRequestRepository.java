package com.chris64233.loancovenant.repo;

import com.chris64233.loancovenant.domain.DrawdownRequest;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface DrawdownRequestRepository extends JpaRepository<DrawdownRequest, Long> {
    Optional<DrawdownRequest> findByBusinessNo(String businessNo);

    List<DrawdownRequest> findByFacilityIdOrderByIdAsc(Long facilityId);

    /**
     * 提款行悲观写锁：在已持有授信行锁的前提下调用（统一加锁顺序
     * “授信行 → 提款行 → 快照行”），串行化同一笔提款的并发批准，
     * 并强制在锁内读取最新状态，避免基于旧 PENDING 重复占用额度。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DrawdownRequest d where d.businessNo = :businessNo")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    Optional<DrawdownRequest> findByBusinessNoForUpdate(@Param("businessNo") String businessNo);

    /** 关联引用投影：避免在加锁前把提款实体装入持久化上下文。 */
    interface DrawdownRefs {
        Long getFacilityId();

        Long getSnapshotId();
    }

    @Query("select d.facility.id as facilityId, d.snapshot.id as snapshotId "
            + "from DrawdownRequest d where d.businessNo = :businessNo")
    Optional<DrawdownRefs> findRefIdsByBusinessNo(@Param("businessNo") String businessNo);

    @Query("select d.facility.id from DrawdownRequest d where d.businessNo = :businessNo")
    Optional<Long> findFacilityIdByBusinessNo(@Param("businessNo") String businessNo);
}
