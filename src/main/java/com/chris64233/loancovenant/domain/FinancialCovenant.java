package com.chris64233.loancovenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

/**
 * 财务契约配置：某指标必须按 {@link CovenantOperator} 方向满足阈值。
 */
@Entity
public class FinancialCovenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private CreditFacility facility;

    /** 指标键，例如 DEBT_TO_EBITDA、CURRENT_RATIO、NET_WORTH。 */
    @Column(nullable = false)
    private String metricKey;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CovenantOperator operator;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal threshold;

    protected FinancialCovenant() {
    }

    public FinancialCovenant(String metricKey, String displayName,
                             CovenantOperator operator, BigDecimal threshold) {
        this.metricKey = metricKey;
        this.displayName = displayName;
        this.operator = operator;
        this.threshold = threshold;
    }

    void attachFacility(CreditFacility facility) {
        this.facility = facility;
    }

    public Long getId() {
        return id;
    }

    public CreditFacility getFacility() {
        return facility;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CovenantOperator getOperator() {
        return operator;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }
}
