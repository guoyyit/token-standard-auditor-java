package com.github.guoyyit.tokenstandardauditorjava.domain;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Points to the exact place a finding refers to: either an ABI element
 * (function/event identified by name and signature) or a bytecode offset.
 */
public final class Location {

    public enum Kind {
        ABI_FUNCTION,
        ABI_EVENT,
        BYTECODE
    }

    private final Kind kind;
    private final String elementName;
    private final String signature;
    private final Long bytecodeOffset;

    @JsonCreator
    public Location(@JsonProperty("kind") Kind kind,
                    @JsonProperty("elementName") String elementName,
                    @JsonProperty("signature") String signature,
                    @JsonProperty("bytecodeOffset") Long bytecodeOffset) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.elementName = elementName;
        this.signature = signature;
        this.bytecodeOffset = bytecodeOffset;
    }

    public static Location abiFunction(String name, String signature) {
        return new Location(Kind.ABI_FUNCTION, name, signature, null);
    }

    public static Location abiEvent(String name, String signature) {
        return new Location(Kind.ABI_EVENT, name, signature, null);
    }

    public static Location bytecode(long offset) {
        return new Location(Kind.BYTECODE, null, null, offset);
    }

    public Kind getKind() {
        return kind;
    }

    public String getElementName() {
        return elementName;
    }

    public String getSignature() {
        return signature;
    }

    public Long getBytecodeOffset() {
        return bytecodeOffset;
    }
}
