package com.chris64233.loancovenant.domain;

/**
 * 授信状态。
 */
public enum FacilityStatus {
    /** 正常，可提交并批准提款。 */
    ACTIVE,
    /** 已冻结：存续提款仍可拨付/还款，但不能再批准新提款。 */
    FROZEN,
    /** 已关闭。 */
    CLOSED
}
