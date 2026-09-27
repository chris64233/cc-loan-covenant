package com.chris64233.loancovenant.domain;

/**
 * 提款状态。
 */
public enum DrawdownStatus {
    /** 已提交，等待批准。 */
    PENDING,
    /** 已批准：额度已一次性占用，尚未拨付。可取消或拨付。 */
    APPROVED,
    /** 已批准提款在拨付前取消，额度已释放。 */
    CANCELLED,
    /** 已拨付，只能通过还款减少已用额度。 */
    DISBURSED,
    /** 已拨付提款全部还清。 */
    REPAID
}
