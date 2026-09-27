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
import java.time.Instant;

/**
 * 额度台账流水：只追加、不可修改、不可删除。
 *
 * <p>每条流水记录变动方向、金额及变动后已用额度快照，完整解释
 * 授信已用额度的构成。
 */
@Entity
public class LimitLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private CreditFacility facility;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drawdown_id", nullable = false)
    private DrawdownRequest drawdown;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntryType type;

    /** 本次变动金额，恒为正数。 */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** 变动后的已用额度。 */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal usedAmountAfter;

    @Column(nullable = false)
    private Instant occurredAt;

    protected LimitLedgerEntry() {
    }

    public LimitLedgerEntry(CreditFacility facility, DrawdownRequest drawdown, LedgerEntryType type,
                            BigDecimal amount, BigDecimal usedAmountAfter, Instant occurredAt) {
        this.facility = facility;
        this.drawdown = drawdown;
        this.type = type;
        this.amount = amount;
        this.usedAmountAfter = usedAmountAfter;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public CreditFacility getFacility() {
        return facility;
    }

    public DrawdownRequest getDrawdown() {
        return drawdown;
    }

    public LedgerEntryType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getUsedAmountAfter() {
        return usedAmountAfter;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
