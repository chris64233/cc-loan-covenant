package com.chris64233.loancovenant.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chris64233.loancovenant.api.dto.CovenantCheckItem;
import com.chris64233.loancovenant.api.dto.CovenantView;
import com.chris64233.loancovenant.api.dto.DrawdownView;
import com.chris64233.loancovenant.api.dto.FacilityView;
import com.chris64233.loancovenant.api.dto.LedgerEntryView;
import com.chris64233.loancovenant.api.dto.SnapshotView;
import com.chris64233.loancovenant.domain.CreditFacility;
import com.chris64233.loancovenant.domain.DrawdownRequest;
import com.chris64233.loancovenant.domain.FinancialCovenant;
import com.chris64233.loancovenant.domain.FinancialSnapshot;
import com.chris64233.loancovenant.domain.LimitLedgerEntry;
import com.chris64233.loancovenant.domain.SnapshotStatus;

/** 实体到只读视图的转换（须在持有持久化上下文的事务内调用以读取懒加载关联）。 */
final class Views {

    private Views() {
    }

    static FacilityView toFacilityView(CreditFacility f, List<FinancialCovenant> covenants) {
        List<CovenantView> covenantViews = covenants.stream()
                .map(c -> new CovenantView(c.getId(), c.getMetricCode(), c.getDisplayName(),
                        c.getOperator(), c.getThreshold()))
                .toList();
        return new FacilityView(f.getId(), f.getVersion(), f.getCustomerName(), f.getCurrency(),
                f.getTotalLimit(), f.getUsedLimit(), f.availableLimit(),
                f.getEffectiveFrom(), f.getEffectiveTo(), f.getStatus(), covenantViews);
    }

    static SnapshotView toSnapshotView(FinancialSnapshot s) {
        return new SnapshotView(s.getId(), s.getFacility().getId(), s.getReportPeriod(),
                s.getVersion(), s.getStatus(), new LinkedHashMap<>(s.getMetrics()),
                s.getSubmittedAt());
    }

    static DrawdownView toDrawdownView(DrawdownRequest d) {
        FinancialSnapshot s = d.getSnapshot();
        return new DrawdownView(d.getId(), d.getVersion(), d.getBusinessNo(),
                d.getFacility().getId(), s.getId(), s.getReportPeriod(), s.getVersion(),
                d.getAmount(), d.getStatus(), d.getRepaidAmount(), d.outstandingAmount(),
                d.getApprovalEvidence(), d.getApprovedAt(), d.getCancelledAt(),
                d.getDisbursedAt(), d.getLastRepaidAt(), d.getCreatedAt());
    }

    static LedgerEntryView toLedgerView(LimitLedgerEntry e) {
        return new LedgerEntryView(e.getId(), e.getFacility().getId(), e.getDrawdownBusinessNo(),
                e.getEntryType(), e.getDeltaUsed(), e.getUsedAfter(), e.getRecordedAt());
    }

    static CovenantCheckItem toCheckItem(FinancialCovenant c, BigDecimal actual, boolean satisfied) {
        String reason;
        if (actual == null) {
            reason = "快照缺少指标 " + c.getMetricCode();
        } else {
            String operator = switch (c.getOperator()) {
                case AT_LEAST -> ">=";
                case AT_MOST -> "<=";
            };
            reason = satisfied
                    ? actual.toPlainString() + " " + operator + " " + c.getThreshold().toPlainString()
                    : actual.toPlainString() + " 不满足 " + operator + " "
                            + c.getThreshold().toPlainString();
        }
        return new CovenantCheckItem(c.getMetricCode(), c.getDisplayName(), c.getOperator(),
                c.getThreshold(), actual, satisfied, reason);
    }

    static Map<String, Object> checkItemAsMap(CovenantCheckItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("metricCode", item.metricCode());
        m.put("operator", item.operator().name());
        m.put("threshold", item.threshold());
        m.put("actualValue", item.actualValue());
        m.put("satisfied", item.satisfied());
        m.put("reason", item.reason());
        return m;
    }

    static boolean isCurrent(FinancialSnapshot s) {
        return s.getStatus() == SnapshotStatus.ACTIVE;
    }
}
