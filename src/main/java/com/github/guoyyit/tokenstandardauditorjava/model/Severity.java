package com.github.guoyyit.tokenstandardauditorjava.model;

/**
 * Severity of a compliance finding.
 */
public enum Severity {
    /** Non-compliance with the target standard. */
    ERROR,
    /** Recommended convention violated, contract still mostly compliant. */
    WARNING,
    /** Informational observation, no compliance impact. */
    INFO
}
