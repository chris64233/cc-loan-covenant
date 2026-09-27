package com.chris64233.loancovenant.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * 提款申请。必须绑定一个明确的财务快照版本。
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_drawdown_business_no", columnNames = "business_no"))
public class DrawdownRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    /** 提款业务号，全局唯一，保证提交/批准幂等。 */
    @Column(name = "business_no", nullable = false, length = 64)
    private String businessNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private CreditFacility facility;

    /** 提款绑定的财务快照版本（不可变）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private FinancialSnapshot snapshot;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DrawdownStatus status = DrawdownStatus.PENDING;

    /** 批准时记录的判断依据（契约结果、有效期、额度快照），JSON。 */
    @Column(length = 4000)
    private String approvalEvidence;

    private Instant approvedAt;
    private Instant cancelledAt;
    private Instant disbursedAt;

    /** 已还款金额。 */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal repaidAmount = BigDecimal.ZERO;

    private Instant lastRepaidAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DrawdownRequest() {
    }

    public DrawdownRequest(String businessNo, CreditFacility facility,
                           FinancialSnapshot snapshot, BigDecimal amount) {
        this.businessNo = businessNo;
        this.facility = facility;
        this.snapshot = snapshot;
        this.amount = amount;
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

    public void markApproved(String evidence) {
        this.status = DrawdownStatus.APPROVED;
        this.approvalEvidence = evidence;
        this.approvedAt = Instant.now();
    }

    public void markCancelled() {
        this.status = DrawdownStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public void markDisbursed() {
        this.status = DrawdownStatus.DISBURSED;
        this.disbursedAt = Instant.now();
    }

    /**
     * 登记还款；还清后置为 REPAID。
     *
     * @return 还款后的累计已还金额
     */
    public BigDecimal applyRepayment(BigDecimal payment) {
        BigDecimal newRepaid = this.repaidAmount.add(payment);
        this.repaidAmount = newRepaid;
        this.lastRepaidAt = Instant.now();
        if (newRepaid.compareTo(amount) >= 0) {
            this.status = DrawdownStatus.REPAID;
        }
        return newRepaid;
    }

    /** 剩余未还本金。 */
    public BigDecimal outstandingAmount() {
        return amount.subtract(repaidAmount);
    }

    public LocalDate boundReportPeriod() {
        return snapshot.getReportPeriod();
    }

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
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

    public String getApprovalEvidence() {
        return approvalEvidence;
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

    public BigDecimal getRepaidAmount() {
        return repaidAmount;
    }

    public Instant getLastRepaidAt() {
        return lastRepaidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
