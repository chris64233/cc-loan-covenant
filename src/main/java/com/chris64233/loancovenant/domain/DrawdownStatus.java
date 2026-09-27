package com.chris64233.loancovenant.domain;

/**
 * 提款申请生命周期。
 */
public enum DrawdownStatus {
    /** 已提交待批准：不占用额度。 */
    PENDING,
    /** 已批准未拨付：额度已占用，可取消释放。 */
    APPROVED,
    /** 已取消（仅 APPROVED 可取消）：额度已释放。 */
    CANCELLED,
    /** 已拨付：只能通过还款减少已用额度。 */
    DISBURSED,
    /** 已还清：提款结清。 */
    SETTLED,
    /** 批准时业务条件不满足（违约/额度不足），未占用额度。 */
    REJECTED
}
