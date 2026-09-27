package com.chris64233.loancovenant.service;

import org.springframework.http.HttpStatus;

/**
 * 基于旧状态的操作与当前状态冲突：
 * 授信被冻结、绑定快照被更正、额度被其他提款占用、状态不允许该操作等。
 */
public class ConflictException extends BusinessRuleException {

    public ConflictException(String code, String message) {
        super(code, HttpStatus.CONFLICT, message);
    }
}
