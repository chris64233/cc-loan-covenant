package com.chris64233.loancovenant.domain;

/**
 * 额度台账变动类型。台账仅追加、不可修改。
 */
public enum LedgerEntryType {
    /** 批准提款：占用额度。 */
    DRAWDOWN_APPROVED,
    /** 取消已批准未拨付提款：释放额度。 */
    DRAWDOWN_CANCELLED,
    /** 还款：减少已用额度。 */
    REPAYMENT
}
