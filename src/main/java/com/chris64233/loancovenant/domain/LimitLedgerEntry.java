package com.chris64233.loancovenant.domain;

import java.math.BigDecimal;
import java.time.Instant;

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
import jakarta.persistence.PrePersist;

/**
 * 额度台账条目：只追加、不更新、不删除。
 * 每笔批准/取消/还款都对应一条，并记录变动后的已用额度。
 */
@Entity
public class LimitLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false, updatable = false)
    private CreditFacility facility;

    /** 关联提款业务号（台账追加后不因提款状态变化而修改）。 */
    @Column(name = "drawdown_business_no", length = 64, updatable = false)
    private String drawdownBusinessNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24, updatable = false)
    private LedgerEntryType entryType;

    /** 对已用额度的影响：占用为正、释放/还款为负。 */
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal deltaUsed;

    /** 本条记账后的已用额度。 */
    @Column(name = "used_after", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal usedAfter;

    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    protected LimitLedgerEntry() {
    }

    public LimitLedgerEntry(CreditFacility facility, String drawdownBusinessNo,
                            LedgerEntryType entryType, BigDecimal deltaUsed, BigDecimal usedAfter) {
        this.facility = facility;
        this.drawdownBusinessNo = drawdownBusinessNo;
        this.entryType = entryType;
        this.deltaUsed = deltaUsed;
        this.usedAfter = usedAfter;
    }

    @PrePersist
    void onCreate() {
        this.recordedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CreditFacility getFacility() {
        return facility;
    }

    public String getDrawdownBusinessNo() {
        return drawdownBusinessNo;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getDeltaUsed() {
        return deltaUsed;
    }

    public BigDecimal getUsedAfter() {
        return usedAfter;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
