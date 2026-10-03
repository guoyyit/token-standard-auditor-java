package com.github.guoyyit.tokenstandardauditorjava.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable result of a completed audit check.
 */
public final class AuditReport {

    private final String checkId;
    private final String contractKey;
    private final TokenStandard standard;
    private final String standardVersion;
    private final CheckStatus status;
    private final List<Finding> findings;
    private final String errorMessage;
    private final Instant createdAt;
    private final Instant completedAt;

    @JsonCreator
    public AuditReport(@JsonProperty("checkId") String checkId,
                       @JsonProperty("contractKey") String contractKey,
                       @JsonProperty("standard") TokenStandard standard,
                       @JsonProperty("standardVersion") String standardVersion,
                       @JsonProperty("status") CheckStatus status,
                       @JsonProperty("findings") List<Finding> findings,
                       @JsonProperty("errorMessage") String errorMessage,
                       @JsonProperty("createdAt") Instant createdAt,
                       @JsonProperty("completedAt") Instant completedAt) {
        this.checkId = Objects.requireNonNull(checkId, "checkId");
        this.contractKey = Objects.requireNonNull(contractKey, "contractKey");
        this.standard = Objects.requireNonNull(standard, "standard");
        this.standardVersion = Objects.requireNonNull(standardVersion, "standardVersion");
        this.status = Objects.requireNonNull(status, "status");
        this.findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        this.errorMessage = errorMessage;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.completedAt = completedAt;
    }

    public String getCheckId() {
        return checkId;
    }

    public String getContractKey() {
        return contractKey;
    }

    public TokenStandard getStandard() {
        return standard;
    }

    public String getStandardVersion() {
        return standardVersion;
    }

    public CheckStatus getStatus() {
        return status;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
