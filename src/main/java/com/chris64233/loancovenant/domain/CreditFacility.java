package com.chris64233.loancovenant.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业授信：记录币种、总额度、有效期。
 *
 * <p>{@code version} 为乐观锁版本，{@code usedAmount} 的并发修改同时配合
 * 数据库行悲观锁保证不超额；{@code frozen} 为审批冻结标记，冻结期间任何
 * 基于旧状态的批准都将返回冲突。
 */
@Entity
public class CreditFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalLimit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal usedAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean frozen = false;

    @Version
    private long version;

    @OneToMany(mappedBy = "facility", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FinancialCovenant> covenants = new ArrayList<>();

    protected CreditFacility() {
    }

    public CreditFacility(String customerName, String currency, BigDecimal totalLimit,
                          LocalDate startDate, LocalDate endDate) {
        this.customerName = customerName;
        this.currency = currency;
        this.totalLimit = totalLimit;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void addCovenant(FinancialCovenant covenant) {
        covenant.attachFacility(this);
        this.covenants.add(covenant);
    }

    public Long getId() {
        return id;
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

    public BigDecimal getUsedAmount() {
        return usedAmount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public long getVersion() {
        return version;
    }

    public List<FinancialCovenant> getCovenants() {
        return covenants;
    }

    /** 冻结授信（审批期间冻结）。 */
    public void freeze() {
        this.frozen = true;
    }

    /** 解除冻结。 */
    public void unfreeze() {
        this.frozen = false;
    }

    /** 批准时一次性增加已用额度；超额由调用方先行校验。 */
    public void reserve(BigDecimal amount) {
        this.usedAmount = this.usedAmount.add(amount);
    }

    /** 取消或还款时减少已用额度。 */
    public void release(BigDecimal amount) {
        BigDecimal next = this.usedAmount.subtract(amount);
        if (next.signum() < 0) {
            throw new IllegalStateException("已用额度不能为负: " + next);
        }
        this.usedAmount = next;
    }

    public BigDecimal getAvailableAmount() {
        return totalLimit.subtract(usedAmount);
    }
}
