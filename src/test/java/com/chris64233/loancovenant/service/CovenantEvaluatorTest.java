package com.chris64233.loancovenant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.loancovenant.domain.CovenantOperator;
import com.chris64233.loancovenant.domain.FinancialCovenant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CovenantEvaluatorTest {

    private final CovenantEvaluator evaluator = new CovenantEvaluator();

    private FinancialCovenant covenant(String key, CovenantOperator op, String threshold) {
        return new FinancialCovenant(key, key, op, new BigDecimal(threshold));
    }

    private com.chris64233.loancovenant.domain.FinancialSnapshot snapshot(Map<String, String> metrics) {
        var map = new java.util.HashMap<String, BigDecimal>();
        metrics.forEach((k, v) -> map.put(k, new BigDecimal(v)));
        return new com.chris64233.loancovenant.domain.FinancialSnapshot(
                null, java.time.LocalDate.of(2026, 6, 30), 1, map,
                java.time.Instant.parse("2026-06-30T10:00:00Z"));
    }

    @Test
    void gte_and_lte_thresholds_evaluated_by_numeric_value() {
        var covenants = List.of(
                covenant("CURRENT_RATIO", CovenantOperator.GTE, "1.50"),
                covenant("DEBT_TO_EBITDA", CovenantOperator.LTE, "3.00"));
        var snap = snapshot(Map.of(
                "CURRENT_RATIO", "1.5",      // 等于阈值，按数值满足
                "DEBT_TO_EBITDA", "2.999")); // 小于阈值

        List<CovenantCheck> checks = evaluator.evaluate(covenants, snap);

        assertThat(checks).hasSize(2);
        assertThat(checks).allMatch(CovenantCheck::satisfied);
    }

    @Test
    void breach_is_reported_per_metric() {
        var covenants = List.of(
                covenant("CURRENT_RATIO", CovenantOperator.GTE, "1.50"),
                covenant("DEBT_TO_EBITDA", CovenantOperator.LTE, "3.00"));
        var snap = snapshot(Map.of(
                "CURRENT_RATIO", "1.20",
                "DEBT_TO_EBITDA", "3.50"));

        List<CovenantCheck> checks = evaluator.evaluate(covenants, snap);

        assertThat(checks).extracting(CovenantCheck::satisfied).containsOnly(false, false);
        assertThat(checks).extracting(CovenantCheck::actualValue)
                .containsExactly(new BigDecimal("1.20"), new BigDecimal("3.50"));
    }

    @Test
    void missing_metric_is_a_breach() {
        var checks = evaluator.evaluate(
                List.of(covenant("NET_WORTH", CovenantOperator.GTE, "1000000")),
                snapshot(Map.of("OTHER", "1")));

        assertThat(checks).hasSize(1);
        assertThat(checks.getFirst().satisfied()).isFalse();
        assertThat(checks.getFirst().actualValue()).isNull();
        assertThat(checks.getFirst().detail()).contains("缺失");
    }
}
