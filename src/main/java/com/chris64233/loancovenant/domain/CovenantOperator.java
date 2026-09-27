package com.chris64233.loancovenant.domain;

/**
 * 财务契约的比较方向。
 */
public enum CovenantOperator {
    /** 指标值 &gt;= 阈值（如资产负债率上限用 DEBT_TO_ASSET &lt;= 阈值，属于 AT_MOST）。 */
    AT_LEAST,
    /** 指标值 &lt;= 阈值。 */
    AT_MOST
}
