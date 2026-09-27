package com.chris64233.loancovenant.service;

import org.springframework.http.HttpStatus;

/** 引用的资源不存在。 */
public class NotFoundException extends BusinessRuleException {

    public NotFoundException(String message) {
        super("NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }
}
