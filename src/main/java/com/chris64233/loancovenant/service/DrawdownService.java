package com.chris64233.loancovenant.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.loancovenant.api.dto.CovenantCheckItem;
import com.chris64233.loancovenant.api.dto.DrawdownView;
import com.chris64233.loancovenant.api.dto.LedgerEntryView;
import com.chris64233.loancovenant.api.dto.RepaymentRequest;
import com.chris64233.loancovenant.api.dto.SubmitDrawdownRequest;
import com.chris64233.loancovenant.domain.CreditFacility;
import com.chris64233.loancovenant.domain.DrawdownRequest;
import com.chris64233.loancovenant.domain.DrawdownStatus;
import com.chris64233.loancovenant.domain.FacilityStatus;
import com.chris64233.loancovenant.domain.FinancialCovenant;
import com.chris64233.loancovenant.domain.FinancialSnapshot;
import com.chris64233.loancovenant.domain.LedgerEntryType;
import com.chris64233.loancovenant.domain.LimitLedgerEntry;
import com.chris64233.loancovenant.domain.SnapshotStatus;
import com.chris64233.loancovenant.repo.CreditFacilityRepository;
import com.chris64233.loancovenant.repo.DrawdownRequestRepository;
import com.chris64233.loancovenant.repo.FinancialCovenantRepository;
import com.chris64233.loancovenant.repo.FinancialSnapshotRepository;
import com.chris64233.loancovenant.repo.LimitLedgerEntryRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class DrawdownService {

    private final DrawdownRequestRepository drawdownRepository;
    private final CreditFacilityRepository facilityRepository;
    private final FinancialSnapshotRepository snapshotRepository;
    private final FinancialCovenantRepository covenantRepository;
    private final LimitLedgerEntryRepository ledgerRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public DrawdownService(DrawdownRequestRepository drawdownRepository,
                           CreditFacilityRepository facilityRepository,
                           FinancialSnapshotRepository snapshotRepository,
                           FinancialCovenantRepository covenantRepository,
                           LimitLedgerEntryRepository ledgerRepository,
                           ObjectMapper objectMapper, Clock clock) {
        this.drawdownRepository = drawdownRepository;
        this.facilityRepository = facilityRepository;
        this.snapshotRepository = snapshotRepository;
        this.covenantRepository = covenantRepository;
        this.ledgerRepository = ledgerRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 提交提款：绑定一个明确的快照版本。业务号幂等——同一业务号重复提交返回既有申请。
     */
    @Transactional
    public DrawdownView submit(Long facilityId, SubmitDrawdownRequest request) {
        var existing = drawdownRepository.findByBusinessNo(request.businessNo());
        if (existing.isPresent()) {
            DrawdownRequest d = existing.get();
            if (!d.getFacility().getId().equals(facilityId)) {
                throw new ConflictException("BUSINESS_NO_CONFLICT",
                        "提款业务号已存在: " + request.businessNo());
            }
            return Views.toDrawdownView(d);
        }
        CreditFacility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new NotFoundException("授信不存在: " + facilityId));
        FinancialSnapshot snapshot = snapshotRepository.findById(request.snapshotId())
                .orElseThrow(() -> new NotFoundException(
                        "财务快照不存在: " + request.snapshotId()));
        if (!snapshot.getFacility().getId().equals(facilityId)) {
            throw new NotFoundException("财务快照不存在: " + request.snapshotId());
        }
        // 允许绑定历史版本；该版本若已被更正，将在批准时按 SNAPSHOT_CORRECTED 返回冲突。
        DrawdownRequest drawdown = new DrawdownRequest(request.businessNo(), facility,
                snapshot, request.amount());
        try {
            drawdown = drawdownRepository.saveAndFlush(drawdown);
        } catch (DataIntegrityViolationException e) {
            // 并发提交相同业务号：以先入库者为准，幂等返回。
            DrawdownRequest winner = drawdownRepository.findByBusinessNo(request.businessNo())
                    .orElseThrow(() -> e);
            if (!winner.getFacility().getId().equals(facilityId)) {
                throw new ConflictException("BUSINESS_NO_CONFLICT",
                        "提款业务号已存在: " + request.businessNo());
            }
            return Views.toDrawdownView(winner);
        }
        return Views.toDrawdownView(drawdown);
    }

    /**
     * 批准提款。在同一事务、同一授信行锁内一次性完成全部校验与额度占用，
     * 任何条件不满足都整体回滚，绝不留下部分提款。
     */
    @Transactional
    public DrawdownView approve(Long facilityId, String businessNo) {
        DrawdownRequest drawdown = requireDrawdown(facilityId, businessNo);
        if (drawdown.getStatus() != DrawdownStatus.PENDING) {
            // 已终态：批准幂等返回；已取消不能复活。
            if (drawdown.getStatus() == DrawdownStatus.CANCELLED) {
                throw new ConflictException("DRAWDOWN_CANCELLED",
                        "提款已取消，不能批准: " + businessNo);
            }
            return Views.toDrawdownView(drawdown);
        }
        // 先锁授信行：串行化同一授信的所有额度变动，并与快照更正互斥。
        CreditFacility facility = lockFacility(facilityId);
        // 锁内重新读取，确保基于最新状态判断（实体已随锁刷新）。
        reloadUnderLock(drawdown);
        if (drawdown.getStatus() != DrawdownStatus.PENDING) {
            throw new ConflictException("DRAWDOWN_STATE_CHANGED",
                    "审批期间提款状态已变为 " + drawdown.getStatus());
        }

        if (facility.getStatus() != FacilityStatus.ACTIVE) {
            throw new ConflictException("FACILITY_NOT_ACTIVE",
                    "授信当前状态为 " + facility.getStatus() + "，审批期间不能批准提款");
        }
        LocalDate today = LocalDate.now(clock);
        if (!facility.isEffectiveOn(today)) {
            throw new DrawdownRejectedException(
                    "授信在 " + today + " 不在有效期 [" + facility.getEffectiveFrom()
                            + ", " + facility.getEffectiveTo() + "] 内");
        }
        FinancialSnapshot snapshot = drawdown.getSnapshot();
        if (snapshot.getStatus() != SnapshotStatus.ACTIVE) {
            throw new ConflictException("SNAPSHOT_CORRECTED",
                    "绑定快照 v" + snapshot.getVersion() + " 已在审批期间被更正，请基于最新版本重新申请");
        }
        List<FinancialCovenant> covenants =
                covenantRepository.findByFacilityIdOrderByIdAsc(facilityId);
        List<CovenantCheckItem> checks = covenants.stream().map(c -> {
            var actual = snapshot.metricValue(c.getMetricCode());
            return Views.toCheckItem(c, actual, c.isSatisfiedBy(actual));
        }).toList();
        List<CovenantCheckItem> breached = checks.stream()
                .filter(i -> !i.satisfied()).toList();
        if (!breached.isEmpty()) {
            throw new DrawdownRejectedException(
                    "财务契约不满足: " + breached.stream()
                            .map(CovenantCheckItem::reason).toList());
        }

        BigDecimal usedBefore = facility.getUsedLimit();
        BigDecimal amount = drawdown.getAmount();
        if (amount.compareTo(facility.availableLimit()) > 0) {
            throw new ConflictException("LIMIT_EXCEEDED",
                    "剩余额度不足: 申请 " + amount.toPlainString() + "，可用 "
                            + facility.availableLimit().toPlainString()
                            + "（额度可能已在审批期间被其他提款占用）");
        }
        // 一次性占用额度并记账。
        BigDecimal usedAfter = usedBefore.add(amount);
        facility.setUsedLimit(usedAfter);
        facilityRepository.saveAndFlush(facility);

        String evidence = buildEvidence(facility, snapshot, today, checks, amount,
                usedBefore, usedAfter);
        drawdown.markApproved(evidence);
        drawdownRepository.saveAndFlush(drawdown);

        ledgerRepository.save(new LimitLedgerEntry(facility, businessNo,
                LedgerEntryType.DRAWDOWN_APPROVED, amount, usedAfter));
        return Views.toDrawdownView(drawdown);
    }

    /** 拨付：仅已批准未拨付的提款可拨付，不改变已用额度。 */
    @Transactional
    public DrawdownView disburse(Long facilityId, String businessNo) {
        DrawdownRequest drawdown = requireDrawdown(facilityId, businessNo);
        if (drawdown.getStatus() == DrawdownStatus.DISBURSED
                || drawdown.getStatus() == DrawdownStatus.REPAID) {
            return Views.toDrawdownView(drawdown);
        }
        if (drawdown.getStatus() != DrawdownStatus.APPROVED) {
            throw new ConflictException("INVALID_DRAWDOWN_STATE",
                    "只有已批准未拨付的提款可以拨付，当前状态 " + drawdown.getStatus());
        }
        drawdown.markDisbursed();
        return Views.toDrawdownView(drawdownRepository.saveAndFlush(drawdown));
    }

    /**
     * 取消已批准未拨付的提款并释放额度；提交后未批准的提款不能取消。
     */
    @Transactional
    public DrawdownView cancel(Long facilityId, String businessNo) {
        DrawdownRequest drawdown = requireDrawdown(facilityId, businessNo);
        if (drawdown.getStatus() == DrawdownStatus.CANCELLED) {
            return Views.toDrawdownView(drawdown);
        }
        if (drawdown.getStatus() != DrawdownStatus.APPROVED) {
            throw new ConflictException("INVALID_DRAWDOWN_STATE",
                    "只有已批准未拨付的提款可以取消，当前状态 " + drawdown.getStatus());
        }
        CreditFacility facility = lockFacility(facilityId);
        reloadUnderLock(drawdown);
        if (drawdown.getStatus() != DrawdownStatus.APPROVED) {
            throw new ConflictException("INVALID_DRAWDOWN_STATE",
                    "提款状态已变化: " + drawdown.getStatus());
        }
        BigDecimal amount = drawdown.getAmount();
        BigDecimal usedAfter = facility.getUsedLimit().subtract(amount);
        facility.setUsedLimit(usedAfter);
        facilityRepository.saveAndFlush(facility);
        drawdown.markCancelled();
        drawdownRepository.saveAndFlush(drawdown);
        ledgerRepository.save(new LimitLedgerEntry(facility, businessNo,
                LedgerEntryType.DRAWDOWN_CANCELLED, amount.negate(), usedAfter));
        return Views.toDrawdownView(drawdown);
    }

    /**
     * 还款：仅已拨付提款可还款，减少已用额度并追加台账；可部分还款，还清后状态 REPAID。
     */
    @Transactional
    public DrawdownView repay(Long facilityId, String businessNo, RepaymentRequest request) {
        DrawdownRequest drawdown = requireDrawdown(facilityId, businessNo);
        if (drawdown.getStatus() != DrawdownStatus.DISBURSED
                && drawdown.getStatus() != DrawdownStatus.REPAID) {
            throw new ConflictException("INVALID_DRAWDOWN_STATE",
                    "只有已拨付提款可以还款，当前状态 " + drawdown.getStatus());
        }
        BigDecimal payment = request.amount();
        CreditFacility facility = lockFacility(facilityId);
        // 锁内重读后重新校验，防止并发还款合计超过未还本金。
        reloadUnderLock(drawdown);
        if (drawdown.getStatus() == DrawdownStatus.REPAID) {
            throw new ConflictException("DRAWDOWN_REPAID", "提款已还清");
        }
        if (drawdown.getStatus() != DrawdownStatus.DISBURSED) {
            throw new ConflictException("INVALID_DRAWDOWN_STATE",
                    "提款状态已变化: " + drawdown.getStatus());
        }
        if (payment.compareTo(drawdown.outstandingAmount()) > 0) {
            throw new ValidationException("REPAYMENT_TOO_LARGE",
                    "还款金额 " + payment.toPlainString() + " 超过未还本金 "
                            + drawdown.outstandingAmount().toPlainString());
        }
        BigDecimal usedAfter = facility.getUsedLimit().subtract(payment);
        facility.setUsedLimit(usedAfter);
        facilityRepository.saveAndFlush(facility);
        drawdown.applyRepayment(payment);
        drawdownRepository.saveAndFlush(drawdown);
        ledgerRepository.save(new LimitLedgerEntry(facility, businessNo,
                LedgerEntryType.REPAYMENT, payment.negate(), usedAfter));
        return Views.toDrawdownView(drawdown);
    }

    @Transactional(readOnly = true)
    public DrawdownView get(Long facilityId, String businessNo) {
        return Views.toDrawdownView(requireDrawdown(facilityId, businessNo));
    }

    @Transactional(readOnly = true)
    public List<DrawdownView> list(Long facilityId) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new NotFoundException("授信不存在: " + facilityId);
        }
        return drawdownRepository.findByFacilityIdOrderByIdAsc(facilityId).stream()
                .map(Views::toDrawdownView).toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryView> ledger(Long facilityId) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new NotFoundException("授信不存在: " + facilityId);
        }
        return ledgerRepository.findByFacilityIdOrderByIdAsc(facilityId).stream()
                .map(Views::toLedgerView).toList();
    }

    private DrawdownRequest requireDrawdown(Long facilityId, String businessNo) {
        DrawdownRequest drawdown = drawdownRepository.findByBusinessNo(businessNo)
                .orElseThrow(() -> new NotFoundException(
                        "提款不存在: " + businessNo));
        if (!drawdown.getFacility().getId().equals(facilityId)) {
            throw new NotFoundException("提款不存在: " + businessNo);
        }
        return drawdown;
    }

    private CreditFacility lockFacility(Long facilityId) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new NotFoundException("授信不存在: " + facilityId);
        }
        return facilityRepository.lockById(facilityId);
    }

    /**
     * 在已持有授信行锁的前提下，悲观锁刷新提款实体，绕过一级缓存拿到最新状态。
     * 授信行锁已保证不会有并发额度变动，这里的锁用于确保读到已提交的最新行。
     */
    private void reloadUnderLock(DrawdownRequest drawdown) {
        entityManager.flush();
        entityManager.refresh(drawdown, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    private String buildEvidence(CreditFacility facility, FinancialSnapshot snapshot,
                                 LocalDate today, List<CovenantCheckItem> checks,
                                 BigDecimal drawdownAmount,
                                 BigDecimal usedBefore, BigDecimal usedAfter) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("evaluatedOn", today.toString());
        evidence.put("facilityVersion", facility.getVersion());
        evidence.put("totalLimit", facility.getTotalLimit());
        evidence.put("usedBefore", usedBefore);
        evidence.put("drawdownAmount", drawdownAmount);
        evidence.put("usedAfter", usedAfter);
        evidence.put("effective", facility.isEffectiveOn(today));
        evidence.put("snapshotId", snapshot.getId());
        evidence.put("reportPeriod", snapshot.getReportPeriod().toString());
        evidence.put("snapshotVersion", snapshot.getVersion());
        evidence.put("covenants", checks.stream().map(Views::checkItemAsMap).toList());
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("生成批准依据失败", e);
        }
    }
}
