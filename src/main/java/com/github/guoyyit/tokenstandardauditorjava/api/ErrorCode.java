package com.github.guoyyit.tokenstandardauditorjava.api;

/**
 * Machine-distinguishable error categories for rejected submissions and
 * failed checks. Never silently skipped: every failure maps to one code.
 */
public enum ErrorCode {
    /** ABI JSON missing, malformed, or containing unknown/unsupported types. */
    ABI_PARSE_ERROR,
    /** Bytecode missing, not valid hex, or not parseable as EVM bytecode. */
    BYTECODE_PARSE_ERROR,
    /** Requested standard or standard version has no registered rule set. */
    UNKNOWN_STANDARD_VERSION,
    /** Check did not finish within the configured timeout. */
    TIMEOUT,
    /** Check was cancelled before completion. */
    CANCELLED,
    /** Any other unexpected failure. */
    INTERNAL_ERROR
}
