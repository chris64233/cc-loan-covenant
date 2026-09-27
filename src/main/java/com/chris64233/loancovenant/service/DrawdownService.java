package com.chris64233.loancovenant.service;

import com.chris64233.loancovenant.domain.CreditFacility;
import com.chris64233.loancovenant.domain.DrawdownRequest;
import com.chris64233.loancovenant.domain.DrawdownStatus;
import com.chris64233.loancovenant.domain.FinancialSnapshot;
import com.chris64233.loancovenant.domain.LedgerEntryType;
import com.chris64233.loancovenant.domain.LimitLedgerEntry;
import com.chris64233.loancovenant.domain.SnapshotStatus;
import com.chris64233.loancovenant.repo.DrawdownRequestRepository;
import com.chris64233.loancovenant.repo.FinancialSnapshotRepository;
import com.chris64233.loancovenant.repo.LimitLedgerEntryRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.PessimisticLockException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 提款申请与生命周期服务。
 *
 * <p>并发模型（所有额度变动事务统一）：
 * <ol>
 *   <li>先对授信行取悲观写锁（{@code findByIdForUpdate}），再锁提款行、绑定快照行
 *       （统一“授信行 → 提款行 → 快照行”加锁顺序）；同一授信的所有额度变动严格串行。</li>
 *   <li>锁内依次校验：状态、授信冻结、有效期、快照仍为该期最新、
 *       契约逐项满足、剩余额度充足。任一不满足立即抛出，事务回滚，
 *       不会留下任何部分占用或台账流水。</li>
 *   <li>全部通过后一次性 {@code reserve(amount)}、状态置 APPROVED、
 *       写入判断依据 JSON，并追加一条不可变台账流水。</li>
 * </ol>
 * 因此并发批准在数据库层串行，已用额度永远不会超过总额度；
 * 等待锁后基于旧状态的批准会看到冻结/更正/额度被占并返回冲突。
 */
@Service
public class DrawdownService {

    private static final Logger log = LoggerFactory.getLogger(DrawdownService.class);

    private final DrawdownRequestRepository drawdownRepository;
    private final FinancialSnapshotRepository snapshotRepository;
    private final LimitLedgerEntryRepository ledgerRepository;
    private final FacilityService facilityService;
    private final CovenantEvaluator evaluator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public DrawdownService(DrawdownRequestRepository drawdownRepository,
                           FinancialSnapshotRepository snapshotRepository,
                           LimitLedgerEntryRepository ledgerRepository,
                           FacilityService facilityService,
                           CovenantEvaluator evaluator,
                           ObjectMapper objectMapper, Clock clock,
                           TransactionTemplate transactionTemplate) {
        this.drawdownRepository = drawdownRepository;
        this.snapshotRepository = snapshotRepository;
        this.ledgerRepository = ledgerRepository;
        this.facilityService = facilityService;
        this.evaluator = evaluator;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    /** 提交提款申请命令。 */
    public record SubmitDrawdownCommand(String businessNo, Long facilityId,
                                        Long snapshotId, BigDecimal amount) {
    }

    /** 提交结果：携带是否新建，用于 HTTP 201/200 幂等区分。 */
    public record SubmitResult(DrawdownView view, boolean created) {
    }

    // ---------------------------------------------------------------- 提交

    /**
     * 提交提款申请。采用编程式事务：插入与唯一性兜底分属独立事务，
     * 并发同业务号导致唯一约束冲突时，仅该插入事务回滚，
     * 随后在新事务中读取已存在申请返回（幂等），不污染调用方事务。
     */
    public SubmitResult submit(SubmitDrawdownCommand cmd) {
        if (cmd.businessNo() == null || cmd.businessNo().isBlank()) {
            throw new IllegalArgumentException("提款业务号不能为空");
        }
        if (cmd.amount() == null || cmd.amount().signum() <= 0) {
            throw new IllegalArgumentException("提款金额必须为正数");
        }
        try {
            return transactionTemplate.execute(status -> doSubmit(cmd));
        } catch (DataIntegrityViolationException e) {
            // 并发提交同一业务号：唯一约束兜底，新事务读取已存在申请。
            return transactionTemplate.execute(status ->
                    new SubmitResult(toView(requireByBusinessNo(cmd.businessNo())), false));
        }
    }

    private SubmitResult doSubmit(SubmitDrawdownCommand cmd) {
        CreditFacility facility = facilityService.require(cmd.facilityId());
        FinancialSnapshot snapshot = snapshotRepository.findById(cmd.snapshotId())
                .orElseThrow(() -> new NotFoundException("财务快照不存在: " + cmd.snapshotId()));
        if (!snapshot.getFacility().getId().equals(facility.getId())) {
            throw new IllegalArgumentException("快照不属于该授信");
        }
        // 幂等：同一业务号返回既有申请。
        DrawdownRequest existing = drawdownRepository.findByBusinessNo(cmd.businessNo()).orElse(null);
        if (existing != null) {
            return new SubmitResult(toView(existing), false);
        }
        DrawdownRequest request = drawdownRepository.saveAndFlush(new DrawdownRequest(
                cmd.businessNo(), facility, snapshot, cmd.amount(), Instant.now(clock)));
        return new SubmitResult(toView(request), true);
    }

    // ---------------------------------------------------------------- 批准

    /**
     * 批准提款。全部条件满足才一次性占用额度；业务不达标（契约/有效期/超总额度）
     * 终态置 REJECTED（HTTP 422，不占额度）；基于旧状态（冻结/快照被更正/
     * 额度并发被占）抛 {@link ConflictException}（HTTP 409，申请仍为 PENDING）。
     */
    @Transactional
    public DrawdownView approve(String businessNo) {
        // 仅取关联 ID（投影查询，不把实体装入持久化上下文），同时完成 404 检查。
        DrawdownRequestRepository.DrawdownRefs refs =
                drawdownRepository.findRefIdsByBusinessNo(businessNo)
                        .orElseThrow(() -> new NotFoundException("提款申请不存在: " + businessNo));
        Long facilityId = refs.getFacilityId();
        Long snapshotId = refs.getSnapshotId();

        // 1) 授信行悲观写锁：与其他额度变动及快照更严格串行。
        CreditFacility facility;
        try {
            facility = facilityService.requireLocked(facilityId);
        } catch (PessimisticLockException e) {
            throw new ConflictException("FACILITY_LOCKED", "授信正被其他交易处理，请稍后重试");
        }

        // 2) 同一笔提款行加锁并首次加载实体（锁内读到最新状态）：
        //    并发重复批准时，后到者在此看到 APPROVED，幂等返回而非重复占用。
        DrawdownRequest request = drawdownRepository.findByBusinessNoForUpdate(businessNo)
                .orElseThrow(() -> new NotFoundException("提款申请不存在: " + businessNo));
        if (request.getStatus() != DrawdownStatus.PENDING) {
            return toView(request);
        }

        // 3) 审批期间授信被冻结 → 旧批准冲突。
        if (facility.isFrozen()) {
            throw new ConflictException("FACILITY_FROZEN",
                    "授信已被冻结，基于冻结前状态的批准无效");
        }

        // 4) 锁内重读快照，检测审批期间是否被更正。
        FinancialSnapshot snapshot = snapshotRepository.findByIdForUpdate(snapshotId)
                .orElseThrow(() -> new NotFoundException("财务快照不存在: " + snapshotId));
        if (snapshot.getStatus() == SnapshotStatus.SUPERSEDED) {
            throw new ConflictException("SNAPSHOT_SUPERSEDED",
                    "绑定的财务快照 v" + snapshot.getVersionNo()
                            + " 已被新版本更正，请基于最新快照重新申请");
        }

        // 5) 有效期。
        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(facility.getStartDate()) || today.isAfter(facility.getEndDate())) {
            return reject(request, "COVENANT_OR_VALIDITY",
                    "提款日 " + today + " 不在授信有效期 "
                            + facility.getStartDate() + " ~ " + facility.getEndDate() + " 内");
        }

        // 6) 契约逐项判断。
        List<CovenantCheck> checks = evaluator.evaluate(facility.getCovenants(), snapshot);
        List<CovenantCheck> failed = checks.stream().filter(c -> !c.satisfied()).toList();
        if (!failed.isEmpty()) {
            return reject(request, "COVENANT_BREACHED",
                    "以下财务契约不满足: " + failed.stream()
                            .map(c -> c.metricKey() + "(" + c.detail() + ")")
                            .toList());
        }

        // 7) 额度。申请金额本身超过总额度属终态业务拒绝；仅在可用额度不足
        //    （并发被其他提款占用）时返回冲突，可稍后重试。
        if (request.getAmount().compareTo(facility.getTotalLimit()) > 0) {
            return reject(request, "LIMIT_EXCEEDED",
                    "提款金额 " + request.getAmount() + " 超过授信总额度 "
                            + facility.getTotalLimit());
        }
        if (request.getAmount().compareTo(facility.getAvailableAmount()) > 0) {
            throw new ConflictException("LIMIT_EXHAUSTED",
                    "剩余额度不足: 申请 " + request.getAmount() + "，可用 "
                            + facility.getAvailableAmount()
                            + "，额度可能已被其他提款占用");
        }

        // 8) 全部满足：一次性占用 + 台账 + 判断依据。以上任何失败均不会到达此处。
        Instant now = Instant.now(clock);
        BigDecimal usedBefore = facility.getUsedAmount();
        facility.reserve(request.getAmount());
        String basis = buildDecisionBasis(facility, snapshot, checks, today, now,
                request.getAmount(), usedBefore);
        request.approve(basis, now);

        ledgerRepository.save(new LimitLedgerEntry(
                facility, request, LedgerEntryType.DRAWDOWN_APPROVED,
                request.getAmount(), facility.getUsedAmount(), now));
        return toView(request);
    }

    private DrawdownView reject(DrawdownRequest request, String code, String reason) {
        request.reject(reason);
        log.info("提款 {} 终态拒绝[{}]: {}", request.getBusinessNo(), code, reason);
        return toView(request);
    }

    private String buildDecisionBasis(CreditFacility facility, FinancialSnapshot snapshot,
                                      List<CovenantCheck> checks, LocalDate today, Instant now,
                                      BigDecimal requestedAmount, BigDecimal usedBefore) {
        Map<String, Object> basis = new LinkedHashMap<>();
        basis.put("evaluatedAt", now.toString());
        basis.put("drawdownDate", today.toString());
        basis.put("facilityId", facility.getId());
        basis.put("currency", facility.getCurrency());
        basis.put("totalLimit", facility.getTotalLimit());
        basis.put("usedAmountBefore", usedBefore);
        basis.put("requestedAmount", requestedAmount);
        basis.put("availableAmountBefore",
                facility.getTotalLimit().subtract(usedBefore));
        basis.put("snapshotId", snapshot.getId());
        basis.put("reportingPeriod", snapshot.getReportingPeriod().toString());
        basis.put("snapshotVersionNo", snapshot.getVersionNo());
        basis.put("snapshotStatus", snapshot.getStatus().name());
        basis.put("validity", facility.getStartDate() + "~" + facility.getEndDate());
        basis.put("covenantChecks", checks);
        try {
            return objectMapper.writeValueAsString(basis);
        } catch (JacksonException e) {
            throw new IllegalStateException("判断依据序列化失败", e);
        }
    }

    // ------------------------------------------------------------ 取消/拨付/还款

    /** 取消已批准未拨付的提款，一次性释放额度并写台账。 */
    @Transactional
    public DrawdownView cancel(String businessNo) {
        DrawdownRequest request = requireLockedByBusinessNo(businessNo);
        if (request.getStatus() == DrawdownStatus.CANCELLED) {
            return toView(request); // 幂等
        }
        if (request.getStatus() != DrawdownStatus.APPROVED) {
            throw new ConflictException("INVALID_STATUS",
                    "仅已批准未拨付的提款可取消，当前状态: " + request.getStatus());
        }
        Instant now = Instant.now(clock);
        CreditFacility facility = request.getFacility();
        facility.release(request.getAmount());
        request.cancel(now);
        ledgerRepository.save(new LimitLedgerEntry(
                facility, request, LedgerEntryType.DRAWDOWN_CANCELLED,
                request.getAmount(), facility.getUsedAmount(), now));
        return toView(request);
    }

    /** 拨付：APPROVED → DISBURSED，不改变已用额度（批准时已占用）。 */
    @Transactional
    public DrawdownView disburse(String businessNo) {
        DrawdownRequest request = requireLockedByBusinessNo(businessNo);
        if (request.getStatus() == DrawdownStatus.DISBURSED
                || request.getStatus() == DrawdownStatus.SETTLED) {
            return toView(request); // 幂等
        }
        if (request.getStatus() != DrawdownStatus.APPROVED) {
            throw new ConflictException("INVALID_STATUS",
                    "仅已批准提款可拨付，当前状态: " + request.getStatus());
        }
        request.disburse(Instant.now(clock));
        return toView(request);
    }

    /**
     * 还款：仅 DISBURSED（含部分还款后）提款可还，按还款金额释放已用额度并写台账；
     * 还清时状态转 SETTLED。不允许超额还款。
     */
    @Transactional
    public DrawdownView repay(String businessNo, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("还款金额必须为正数");
        }
        DrawdownRequest request = requireLockedByBusinessNo(businessNo);
        DrawdownStatus st = request.getStatus();
        if (st != DrawdownStatus.DISBURSED && st != DrawdownStatus.SETTLED) {
            throw new ConflictException("INVALID_STATUS",
                    "仅已拨付提款可还款，当前状态: " + st);
        }
        if (st == DrawdownStatus.SETTLED || amount.compareTo(request.getOutstandingAmount()) > 0) {
            throw new ConflictException("REPAYMENT_EXCEEDED",
                    "还款金额超过未偿余额: " + request.getOutstandingAmount());
        }
        Instant now = Instant.now(clock);
        CreditFacility facility = request.getFacility();
        facility.release(amount);
        request.addRepayment(amount, now);
        ledgerRepository.save(new LimitLedgerEntry(
                facility, request, LedgerEntryType.REPAYMENT,
                amount, facility.getUsedAmount(), now));
        return toView(request);
    }

    // ---------------------------------------------------------------- 查询

    @Transactional(readOnly = true)
    public DrawdownView getByBusinessNo(String businessNo) {
        return toView(requireByBusinessNo(businessNo));
    }

    @Transactional(readOnly = true)
    public List<DrawdownView> listByFacility(Long facilityId) {
        facilityService.require(facilityId);
        return drawdownRepository.findByFacilityIdOrderByIdAsc(facilityId).stream()
                .map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryView> ledger(Long facilityId) {
        facilityService.require(facilityId);
        return ledgerRepository.findByFacilityIdOrderByIdAsc(facilityId).stream()
                .map(this::toView).toList();
    }

    // ---------------------------------------------------------------- 内部

    private DrawdownRequest requireByBusinessNo(String businessNo) {
        return drawdownRepository.findByBusinessNo(businessNo)
                .orElseThrow(() -> new NotFoundException("提款申请不存在: " + businessNo));
    }

    /**
     * 按统一加锁顺序加载：先授信行，再提款行（首次加载，保证读到锁内最新状态）。
     * 使同一笔提款的取消/拨付/还款/批准彼此互斥。
     */
    private DrawdownRequest requireLockedByBusinessNo(String businessNo) {
        Long facilityId = drawdownRepository.findFacilityIdByBusinessNo(businessNo)
                .orElseThrow(() -> new NotFoundException("提款申请不存在: " + businessNo));
        facilityService.requireLocked(facilityId);
        return drawdownRepository.findByBusinessNoForUpdate(businessNo)
                .orElseThrow(() -> new NotFoundException("提款申请不存在: " + businessNo));
    }

    private DrawdownView toView(DrawdownRequest d) {
        return new DrawdownView(
                d.getId(), d.getBusinessNo(), d.getFacility().getId(),
                d.getFacility().getCurrency(),
                d.getSnapshot().getId(), d.getSnapshot().getStatus().name(),
                d.getAmount(), d.getStatus().name(), d.getDecisionBasis(),
                d.getRejectionReason(), d.getRepaidAmount(), d.getOutstandingAmount(),
                d.getCreatedAt(), d.getApprovedAt(), d.getCancelledAt(),
                d.getDisbursedAt(), d.getSettledAt());
    }

    private LedgerEntryView toView(LimitLedgerEntry e) {
        return new LedgerEntryView(
                e.getId(), e.getFacility().getId(), e.getDrawdown().getId(),
                e.getDrawdown().getBusinessNo(), e.getType().name(),
                e.getAmount(), e.getUsedAmountAfter(), e.getOccurredAt());
    }
}
