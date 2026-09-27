package com.chris64233.loancovenant.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 提款申请：必须明确绑定一个财务快照版本。
 *
 * <p>状态机：PENDING → APPROVED → DISBURSED → SETTLED；APPROVED → CANCELLED；
 * PENDING → REJECTED。批准成功时一次性占用额度并写入判断依据，
 * 不存在部分提款。{@code businessNo} 全局唯一保证幂等。
 */
@Entity
public class DrawdownRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 提款业务号，幂等键。 */
    @Column(nullable = false, unique = true, length = 64)
    private String businessNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private CreditFacility facility;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private FinancialSnapshot snapshot;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DrawdownStatus status = DrawdownStatus.PENDING;

    /** 批准时的契约逐项判断依据（JSON）。 */
    @Column(length = 8000)
    private String decisionBasis;

    /** 拒绝原因（契约违约或额度不足）。 */
    @Column(length = 1000)
    private String rejectionReason;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant approvedAt;
    private Instant cancelledAt;
    private Instant disbursedAt;
    private Instant settledAt;

    /** 已还款金额，仅 DISBURSED 后增加。 */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal repaidAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "drawdown", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LimitLedgerEntry> ledgerEntries = new ArrayList<>();

    protected DrawdownRequest() {
    }

    public DrawdownRequest(String businessNo, CreditFacility facility, FinancialSnapshot snapshot,
                           BigDecimal amount, Instant createdAt) {
        this.businessNo = businessNo;
        this.facility = facility;
        this.snapshot = snapshot;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public void approve(String decisionBasis, Instant at) {
        this.status = DrawdownStatus.APPROVED;
        this.decisionBasis = decisionBasis;
        this.approvedAt = at;
    }

    public void reject(String reason) {
        this.status = DrawdownStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public void cancel(Instant at) {
        this.status = DrawdownStatus.CANCELLED;
        this.cancelledAt = at;
    }

    public void disburse(Instant at) {
        this.status = DrawdownStatus.DISBURSED;
        this.disbursedAt = at;
    }

    public void addRepayment(BigDecimal part, Instant at) {
        this.repaidAmount = this.repaidAmount.add(part);
        if (this.repaidAmount.compareTo(this.amount) >= 0) {
            this.status = DrawdownStatus.SETTLED;
            this.settledAt = at;
        }
    }

    public Long getId() {
        return id;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public CreditFacility getFacility() {
        return facility;
    }

    public FinancialSnapshot getSnapshot() {
        return snapshot;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public DrawdownStatus getStatus() {
        return status;
    }

    public String getDecisionBasis() {
        return decisionBasis;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getDisbursedAt() {
        return disbursedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public BigDecimal getRepaidAmount() {
        return repaidAmount;
    }

    public BigDecimal getOutstandingAmount() {
        return amount.subtract(repaidAmount);
    }

    public List<LimitLedgerEntry> getLedgerEntries() {
        return ledgerEntries;
    }
}
