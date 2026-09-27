package com.chris64233.loancovenant.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

/**
 * 企业授信：记录币种、总额度与有效期，挂多个财务契约。
 */
@Entity
public class CreditFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 乐观锁：状态/额度发生变化时旧版本的写入会冲突。 */
    @Version
    private long version;

    @Column(nullable = false)
    private String customerName;

    /** ISO 4217 币种代码，如 CNY/USD。 */
    @Column(nullable = false, length = 3)
    private String currency;

    /** 总额度，金额一律使用 BigDecimal。 */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalLimit;

    /** 已用额度，只随提款批准/取消/还款变化。 */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal usedLimit = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FacilityStatus status = FacilityStatus.ACTIVE;

    @OneToMany(mappedBy = "facility", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FinancialCovenant> covenants = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CreditFacility() {
    }

    public CreditFacility(String customerName, String currency, BigDecimal totalLimit,
                          LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.customerName = customerName;
        this.currency = currency;
        this.totalLimit = totalLimit;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** 剩余可用额度。 */
    public BigDecimal availableLimit() {
        return totalLimit.subtract(usedLimit);
    }

    public boolean isEffectiveOn(LocalDate date) {
        return !date.isBefore(effectiveFrom) && !date.isAfter(effectiveTo);
    }

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalLimit() {
        return totalLimit;
    }

    public BigDecimal getUsedLimit() {
        return usedLimit;
    }

    public void setUsedLimit(BigDecimal usedLimit) {
        this.usedLimit = usedLimit;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public FacilityStatus getStatus() {
        return status;
    }

    public void setStatus(FacilityStatus status) {
        this.status = status;
    }

    public List<FinancialCovenant> getCovenants() {
        return covenants;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
