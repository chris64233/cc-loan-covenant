package com.chris64233.loancovenant.service;

import org.springframework.http.HttpStatus;

/** 请求数据本身不合法。 */
public class ValidationException extends BusinessRuleException {

    public ValidationException(String code, String message) {
        super(code, HttpStatus.BAD_REQUEST, message);
    }
}
