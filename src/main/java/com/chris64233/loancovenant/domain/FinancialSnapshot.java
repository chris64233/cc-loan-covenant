package com.chris64233.loancovenant.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 企业财务快照：绑定报告期，按版本追加、不可覆盖。
 *
 * <p>同一报告期提交新版本时，旧版本由 CURRENT 转为 SUPERSEDED 但永不删除；
 * 提款一旦绑定某个快照版本，后续即使该版本被更正（superseded），
 * 基于旧版本的批准必须返回冲突。
 */
@Entity
public class FinancialSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private CreditFacility facility;

    @Column(nullable = false)
    private LocalDate reportingPeriod;

    /** 同一报告期内单调递增，从 1 开始。 */
    @Column(nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SnapshotStatus status = SnapshotStatus.CURRENT;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "snapshot_metric", joinColumns = @JoinColumn(name = "snapshot_id", nullable = false))
    @MapKeyColumn(name = "metric_key", length = 64)
    @Column(name = "metric_value", nullable = false, precision = 19, scale = 6)
    private Map<String, BigDecimal> metrics = new HashMap<>();

    @Column(nullable = false)
    private Instant submittedAt;

    protected FinancialSnapshot() {
    }

    public FinancialSnapshot(CreditFacility facility, LocalDate reportingPeriod, int versionNo,
                             Map<String, BigDecimal> metrics, Instant submittedAt) {
        this.facility = facility;
        this.reportingPeriod = reportingPeriod;
        this.versionNo = versionNo;
        this.metrics.putAll(metrics);
        this.submittedAt = submittedAt;
    }

    public Long getId() {
        return id;
    }

    public CreditFacility getFacility() {
        return facility;
    }

    public LocalDate getReportingPeriod() {
        return reportingPeriod;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public SnapshotStatus getStatus() {
        return status;
    }

    public Map<String, BigDecimal> getMetrics() {
        return Collections.unmodifiableMap(metrics);
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    /** 同一报告期出现更新版本时，旧版本被更正取代。 */
    public void markSuperseded() {
        this.status = SnapshotStatus.SUPERSEDED;
    }
}
