package com.github.guoyyit.tokenstandardauditorjava.fixtures;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds ABI JSON strings from function and event signatures.
 */
public final class AbiBuilder {

    private final List<String> entries = new ArrayList<>();

    private AbiBuilder() {
    }

    public static AbiBuilder builder() {
        return new AbiBuilder();
    }

    /**
     * Adds a function. Outputs default to a single bool return.
     */
    public AbiBuilder function(String signature) {
        return function(signature, "bool");
    }

    public AbiBuilder function(String signature, String outputType) {
        String name = signature.substring(0, signature.indexOf('('));
        String args = signature.substring(signature.indexOf('(') + 1, signature.lastIndexOf(')'));
        StringBuilder inputs = new StringBuilder();
        if (!args.isEmpty()) {
            String[] parts = args.split(",");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    inputs.append(",");
                }
                inputs.append("{\"name\":\"arg").append(i).append("\",\"type\":\"").append(parts[i]).append("\"}");
            }
        }
        String outputs = outputType.isEmpty() ? "[]"
                : "[{\"name\":\"\",\"type\":\"" + outputType + "\"}]";
        entries.add("{\"type\":\"function\",\"name\":\"" + name + "\",\"stateMutability\":\"nonpayable\","
                + "\"inputs\":[" + inputs + "],\"outputs\":" + outputs + "}");
        return this;
    }

    public AbiBuilder event(String signature, boolean... indexed) {
        String name = signature.substring(0, signature.indexOf('('));
        String args = signature.substring(signature.indexOf('(') + 1, signature.lastIndexOf(')'));
        StringBuilder inputs = new StringBuilder();
        if (!args.isEmpty()) {
            String[] parts = args.split(",");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    inputs.append(",");
                }
                boolean isIndexed = i < indexed.length && indexed[i];
                inputs.append("{\"name\":\"arg").append(i)
                        .append("\",\"type\":\"").append(parts[i])
                        .append("\",\"indexed\":").append(isIndexed).append("}");
            }
        }
        entries.add("{\"type\":\"event\",\"name\":\"" + name + "\","
                + "\"inputs\":[" + inputs + "]}");
        return this;
    }

    public String build() {
        return "[" + String.join(",", entries) + "]";
    }
}
