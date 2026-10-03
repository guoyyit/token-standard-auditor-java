package com.github.guoyyit.tokenstandardauditorjava.web;

import com.github.guoyyit.tokenstandardauditorjava.api.AuditException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps audit errors to machine-distinguishable API responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuditException.class)
    public ResponseEntity<Map<String, String>> handleAudit(AuditException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", e.getCode().name(), "message", e.getMessage()));
    }
}
