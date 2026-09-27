package com.chris64233.loancovenant.service;

/**
 * 业务条件不满足（HTTP 422）：契约违约或授信已过有效期。
 *
 * <p>与 {@link ConflictException} 的区别：冲突是并发状态竞争，
 * 状态仍可重新决策；本异常表示按当前快照业务上即不达标，
 * 提款被终态置为 REJECTED，但不占用任何额度。
 * 事务不因其回滚（见 noRollbackFor 配置）。
 */
public class BusinessRejectionException extends RuntimeException {

    private final String code;

    public BusinessRejectionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
