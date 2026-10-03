package com.github.guoyyit.tokenstandardauditorjava.model;

/**
 * Lifecycle status of a compliance check.
 */
public enum CheckStatus {
    /** Check has been submitted but not yet started. */
    PENDING,
    /** Check is currently being evaluated. */
    RUNNING,
    /** Check finished successfully; a report is available. */
    COMPLETED,
    /** Check was cancelled by the submitter; no report is available. */
    CANCELLED,
    /** Check exceeded its timeout; no report is available. */
    TIMED_OUT,
    /** Check could not be executed (e.g. parse error); no report is available. */
    FAILED;

    public boolean isTerminal() {
        return this != PENDING && this != RUNNING;
    }
}
