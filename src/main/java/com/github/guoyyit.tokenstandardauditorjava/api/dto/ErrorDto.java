package com.github.guoyyit.tokenstandardauditorjava.api.dto;

/**
 * Structured error response.
 *
 * @param code    machine-readable error code
 * @param message human-readable error message
 */
public record ErrorDto(
        String code,
        String message
) {
}
