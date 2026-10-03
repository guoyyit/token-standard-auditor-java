package com.github.guoyyit.tokenstandardauditorjava.model;

import java.time.Instant;

/**
 * Persisted record of a check submission.
 *
 * <p>A record goes through {@link CheckStatus#PENDING}/{@link CheckStatus#RUNNING} and ends in a
 * terminal state ({@link CheckStatus#COMPLETED}, {@link CheckStatus#CANCELLED},
 * {@link CheckStatus#TIMED_OUT} or {@link CheckStatus#FAILED}). A report is only attached when the
 * check completes successfully.
 */
public class CheckRecord {

    private String id;
    private CheckStatus status;
    private String standard;
    private String version;
    private String abiHash;
    private String bytecodeHash;
    private CheckReport report;
    private String errorCode;
    private String errorMessage;
    private Instant submittedAt;
    private Instant completedAt;
    private boolean cached;
    private String cachedFrom;

    public CheckRecord() {
    }

    public CheckRecord(String id, CheckStatus status, String standard, String version,
                       String abiHash, String bytecodeHash, Instant submittedAt) {
        this.id = id;
        this.status = status;
        this.standard = standard;
        this.version = version;
        this.abiHash = abiHash;
        this.bytecodeHash = bytecodeHash;
        this.submittedAt = submittedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CheckStatus getStatus() {
        return status;
    }

    public void setStatus(CheckStatus status) {
        this.status = status;
    }

    public String getStandard() {
        return standard;
    }

    public void setStandard(String standard) {
        this.standard = standard;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAbiHash() {
        return abiHash;
    }

    public void setAbiHash(String abiHash) {
        this.abiHash = abiHash;
    }

    public String getBytecodeHash() {
        return bytecodeHash;
    }

    public void setBytecodeHash(String bytecodeHash) {
        this.bytecodeHash = bytecodeHash;
    }

    public CheckReport getReport() {
        return report;
    }

    public void setReport(CheckReport report) {
        this.report = report;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }

    public String getCachedFrom() {
        return cachedFrom;
    }

    public void setCachedFrom(String cachedFrom) {
        this.cachedFrom = cachedFrom;
    }
}
