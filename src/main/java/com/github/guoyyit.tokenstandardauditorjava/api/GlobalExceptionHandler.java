package com.github.guoyyit.tokenstandardauditorjava.api;

import com.github.guoyyit.tokenstandardauditorjava.api.dto.ErrorDto;
import com.github.guoyyit.tokenstandardauditorjava.check.CheckService;
import com.github.guoyyit.tokenstandardauditorjava.rule.RuleDescriptorParser;
import com.github.guoyyit.tokenstandardauditorjava.rule.RuleRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to structured error responses with distinguishable error codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CheckService.CheckNotFoundException.class)
    public ResponseEntity<ErrorDto> handleCheckNotFound(CheckService.CheckNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorDto("CHECK_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(RuleRegistry.UnknownStandardException.class)
    public ResponseEntity<ErrorDto> handleUnknownStandard(RuleRegistry.UnknownStandardException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorDto("UNKNOWN_STANDARD", e.getMessage()));
    }

    @ExceptionHandler(RuleRegistry.UnknownVersionException.class)
    public ResponseEntity<ErrorDto> handleUnknownVersion(RuleRegistry.UnknownVersionException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorDto("UNKNOWN_VERSION", e.getMessage()));
    }

    @ExceptionHandler(RuleDescriptorParser.RuleConfigurationException.class)
    public ResponseEntity<ErrorDto> handleRuleConfiguration(RuleDescriptorParser.RuleConfigurationException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorDto("RULE_CONFIGURATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorDto("INVALID_REQUEST", e.getMessage()));
    }
}
