package com.github.guoyyit.tokenstandardauditorjava.domain;

/**
 * Lifecycle states of an audit check.
 */
public enum CheckStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMEOUT
}
