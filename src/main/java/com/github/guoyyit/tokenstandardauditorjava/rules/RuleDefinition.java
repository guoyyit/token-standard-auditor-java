package com.github.guoyyit.tokenstandardauditorjava.rules;

import com.github.guoyyit.tokenstandardauditorjava.domain.Severity;
import com.github.guoyyit.tokenstandardauditorjava.domain.TokenStandard;

import java.util.List;

/**
 * Serializable definition of a versioned rule set for one token standard.
 */
public record RuleDefinition(
        String id,
        TokenStandard standard,
        String version,
        boolean defaultVersion,
        List<FunctionRule> functions,
        List<EventRule> events) {

    public record FunctionRule(
            String name,
            List<String> inputs,
            List<String> outputs,
            String stateMutability,
            boolean required,
            Severity severity) {
    }

    public record EventRule(
            String name,
            List<EventParam> params,
            boolean required,
            Severity severity) {
    }

    public record EventParam(String name, String type, boolean indexed) {
    }
}
