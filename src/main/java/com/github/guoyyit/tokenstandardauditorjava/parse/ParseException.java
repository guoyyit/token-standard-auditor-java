package com.github.guoyyit.tokenstandardauditorjava.parse;

/**
 * Thrown when the contract input cannot be parsed.
 *
 * <p>The {@link #getCode() error code} distinguishes the failure mode so that callers can report a
 * clear, distinguishable error instead of a compliance conclusion.
 */
public class ParseException extends RuntimeException {

    public static final String ABI_PARSE_ERROR = "ABI_PARSE_ERROR";
    public static final String BYTECODE_PARSE_ERROR = "BYTECODE_PARSE_ERROR";

    private final String code;

    public ParseException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ParseException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
