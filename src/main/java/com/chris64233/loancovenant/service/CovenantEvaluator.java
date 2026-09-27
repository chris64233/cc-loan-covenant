package com.chris64233.loancovenant.service;

import com.chris64233.loancovenant.domain.CovenantOperator;
import com.chris64233.loancovenant.domain.FinancialCovenant;
import com.chris64233.loancovenant.domain.FinancialSnapshot;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 财务契约评估器：把快照指标与契约阈值逐项比较。
 *
 * <p>指标缺失视为违约；比较采用 compareTo（按数值而非标度）。
 */
@Component
public class CovenantEvaluator {

    public List<CovenantCheck> evaluate(List<FinancialCovenant> covenants, FinancialSnapshot snapshot) {
        return covenants.stream()
                .map(c -> check(c, snapshot.getMetrics().get(c.getMetricKey())))
                .toList();
    }

    private CovenantCheck check(FinancialCovenant covenant, BigDecimal actual) {
        boolean satisfied;
        String detail;
        if (actual == null) {
            satisfied = false;
            detail = "指标 " + covenant.getMetricKey() + " 在快照中缺失，按违约处理";
        } else if (covenant.getOperator() == CovenantOperator.GTE) {
            satisfied = actual.compareTo(covenant.getThreshold()) >= 0;
            detail = actual.toPlainString() + " >= " + covenant.getThreshold().toPlainString();
        } else {
            satisfied = actual.compareTo(covenant.getThreshold()) <= 0;
            detail = actual.toPlainString() + " <= " + covenant.getThreshold().toPlainString();
        }
        return new CovenantCheck(
                covenant.getMetricKey(),
                covenant.getDisplayName(),
                covenant.getOperator().name(),
                covenant.getThreshold(),
                actual,
                satisfied,
                detail);
    }
}
