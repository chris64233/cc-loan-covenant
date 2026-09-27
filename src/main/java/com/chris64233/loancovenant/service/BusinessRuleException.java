package com.chris64233.loancovenant.service;

import org.springframework.http.HttpStatus;

/**
 * 业务异常基类：携带 HTTP 状态码与稳定的错误代码。
 */
public abstract class BusinessRuleException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    protected BusinessRuleException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
