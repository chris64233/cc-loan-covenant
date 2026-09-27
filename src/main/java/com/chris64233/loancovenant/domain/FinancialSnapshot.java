package com.chris64233.loancovenant.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 企业财务快照。同一授信、同一报告期可多次追加版本：
 * 新版本提交后旧版本标记为 {@link SnapshotStatus#SUPERSEDED}，原始记录保留不覆盖。
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_snapshot_period_version",
        columnNames = {"facility_id", "report_period", "version"}))
public class FinancialSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private CreditFacility facility;

    /** 报告期，如季报期末日 2026-03-31。 */
    @Column(name = "report_period", nullable = false)
    private LocalDate reportPeriod;

    /** 同一报告期内的版本号，从 1 开始递增。 */
    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SnapshotStatus status = SnapshotStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "snapshot_metric",
            joinColumns = @JoinColumn(name = "snapshot_id"))
    @MapKeyColumn(name = "metric_code", length = 64)
    @Column(name = "metric_value", precision = 19, scale = 6)
    private Map<String, BigDecimal> metrics = new HashMap<>();

    @Column(nullable = false, updatable = false)
    private Instant submittedAt;

    protected FinancialSnapshot() {
    }

    public FinancialSnapshot(CreditFacility facility, LocalDate reportPeriod, int version,
                             Map<String, BigDecimal> metrics) {
        this.facility = facility;
        this.reportPeriod = reportPeriod;
        this.version = version;
        if (metrics != null) {
            this.metrics.putAll(metrics);
        }
    }

    @PrePersist
    void onCreate() {
        this.submittedAt = Instant.now();
    }

    public BigDecimal metricValue(String metricCode) {
        return metrics.get(metricCode);
    }

    public Long getId() {
        return id;
    }

    public CreditFacility getFacility() {
        return facility;
    }

    public LocalDate getReportPeriod() {
        return reportPeriod;
    }

    public int getVersion() {
        return version;
    }

    public SnapshotStatus getStatus() {
        return status;
    }

    public void markSuperseded() {
        this.status = SnapshotStatus.SUPERSEDED;
    }

    public Map<String, BigDecimal> getMetrics() {
        return metrics;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
