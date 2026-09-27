package com.chris64233.loancovenant.domain;

/**
 * 不可变额度台账的变动类型。
 */
public enum LedgerEntryType {
    /** 批准提款：占用额度。 */
    DRAWDOWN_APPROVED,
    /** 取消提款：释放占用额度。 */
    DRAWDOWN_CANCELLED,
    /** 还款：减少已拨付占用额度。 */
    REPAYMENT
}
