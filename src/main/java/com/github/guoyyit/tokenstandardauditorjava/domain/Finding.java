package com.github.guoyyit.tokenstandardauditorjava.domain;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single audit conclusion produced by one rule.
 */
public final class Finding {

    private final String ruleId;
    private final Severity severity;
    private final String message;
    private final Location location;

    @JsonCreator
    public Finding(@JsonProperty("ruleId") String ruleId,
                   @JsonProperty("severity") Severity severity,
                   @JsonProperty("message") String message,
                   @JsonProperty("location") Location location) {
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.message = Objects.requireNonNull(message, "message");
        this.location = Objects.requireNonNull(location, "location");
    }

    public String getRuleId() {
        return ruleId;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public Location getLocation() {
        return location;
    }
}
