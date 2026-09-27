package com.chris64233.loancovenant.domain;

import java.math.BigDecimal;

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

/**
 * 财务契约：指定指标（如 debtRatio、currentRatio）与阈值及比较方向。
 */
@Entity
public class FinancialCovenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private CreditFacility facility;

    /** 指标代码，与财务快照中指标键对应。 */
    @Column(nullable = false)
    private String metricCode;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CovenantOperator operator;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal threshold;

    protected FinancialCovenant() {
    }

    public FinancialCovenant(CreditFacility facility, String metricCode, String displayName,
                             CovenantOperator operator, BigDecimal threshold) {
        this.facility = facility;
        this.metricCode = metricCode;
        this.displayName = displayName;
        this.operator = operator;
        this.threshold = threshold;
    }

    /**
     * 判断指标值是否满足契约。指标缺失视为不满足。
     */
    public boolean isSatisfiedBy(BigDecimal metricValue) {
        if (metricValue == null) {
            return false;
        }
        int cmp = metricValue.compareTo(threshold);
        return switch (operator) {
            case AT_LEAST -> cmp >= 0;
            case AT_MOST -> cmp <= 0;
        };
    }

    public Long getId() {
        return id;
    }

    public CreditFacility getFacility() {
        return facility;
    }

    public String getMetricCode() {
        return metricCode;
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
