package com.chris64233.loancovenant.service;

/**
 * 基于旧状态的操作与当前状态冲突（HTTP 409）。
 *
 * <p>典型场景：审批期间授信被冻结、绑定的快照版本已被更正、
 * 剩余额度已被其他并发提款占用。请求方应基于最新状态重新决策。
 */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
