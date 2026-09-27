package com.chris64233.loancovenant.service;

import org.springframework.http.HttpStatus;

/**
 * 提款条件不满足（契约违约、超出有效期、剩余额度不足）。
 * 与 {@link ConflictException} 的区别：这是当前数据下的确定性业务拒绝，
 * 而不是"审批期间状态被改动"造成的过期操作冲突。
 */
public class DrawdownRejectedException extends BusinessRuleException {

    public DrawdownRejectedException(String message) {
        super("DRAWDOWN_REJECTED", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
