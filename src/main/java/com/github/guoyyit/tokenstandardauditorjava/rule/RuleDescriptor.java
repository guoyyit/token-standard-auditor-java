package com.github.guoyyit.tokenstandardauditorjava.rule;

import java.util.Map;

/**
 * Declarative description of a rule, used for runtime rule-set updates.
 *
 * @param id          unique rule identifier
 * @param severity    severity name (ERROR, WARNING, INFO)
 * @param description human-readable description
 * @param type        rule type (see {@link RuleType})
 * @param params      type-specific parameters
 */
public record RuleDescriptor(
        String id,
        String severity,
        String description,
        String type,
        Map<String, Object> params
) {
    /**
     * Supported rule types for declarative rule configuration.
     */
    public enum RuleType {
        REQUIRED_FUNCTION,
        REQUIRED_EVENT,
        FUNCTION_OUTPUT,
        FUNCTION_MUTABILITY,
        ABI_BYTECODE_CONSISTENCY,
        OPTIONAL_FUNCTION,
        EVENT_INDEXING
    }
}
