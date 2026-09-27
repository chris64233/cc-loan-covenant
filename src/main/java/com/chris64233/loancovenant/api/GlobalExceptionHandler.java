package com.chris64233.loancovenant.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chris64233.loancovenant.service.BusinessRuleException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessRuleException ex,
                                                              HttpServletRequest request) {
        return body(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
        Map<String, Object> b = baseBody(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "请求参数校验失败", request.getRequestURI());
        b.put("fields", fields);
        return ResponseEntity.badRequest().body(b);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return body(HttpStatus.CONFLICT, "STATE_CONFLICT",
                "数据已被其他事务修改，请基于最新状态重试", request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code,
                                                     String message, String path) {
        return ResponseEntity.status(status).body(baseBody(status, code, message, path));
    }

    private Map<String, Object> baseBody(HttpStatus status, String code,
                                         String message, String path) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("timestamp", Instant.now().toString());
        b.put("status", status.value());
        b.put("code", code);
        b.put("message", message);
        b.put("path", path);
        return b;
    }
}
