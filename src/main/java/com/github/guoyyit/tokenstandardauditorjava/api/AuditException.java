package com.github.guoyyit.tokenstandardauditorjava.api;

/**
 * Exception carrying a machine-distinguishable {@link ErrorCode}.
 */
public class AuditException extends RuntimeException {

    private final ErrorCode code;

    public AuditException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AuditException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
