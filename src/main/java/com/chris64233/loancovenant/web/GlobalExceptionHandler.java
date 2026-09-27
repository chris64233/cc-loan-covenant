package com.chris64233.loancovenant.web;

import com.chris64233.loancovenant.service.BusinessRejectionException;
import com.chris64233.loancovenant.service.ConflictException;
import com.chris64233.loancovenant.service.NotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一异常到 HTTP 状态码与错误体的映射。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private Map<String, Object> body(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        m.put("timestamp", Instant.now().toString());
        return m;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(BusinessRejectionException.class)
    public ResponseEntity<Map<String, Object>> rejected(BusinessRejectionException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(body(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(body("INVALID_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.put(fe.getField(),
                        fe.getDefaultMessage() == null ? "无效" : fe.getDefaultMessage()));
        Map<String, Object> b = body("VALIDATION_FAILED", "请求参数校验失败");
        b.put("fields", fields);
        return ResponseEntity.badRequest().body(b);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(body("MALFORMED_JSON", "请求体无法解析"));
    }

    @ExceptionHandler({CannotAcquireLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> lock(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("STATE_CONFLICT", "授信状态已被并发交易改变，请基于最新状态重试"));
    }
}
