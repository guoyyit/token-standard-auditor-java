package com.github.guoyyit.tokenstandardauditorjava.api.dto;

import com.github.guoyyit.tokenstandardauditorjava.rule.RuleDescriptor;

import java.util.Map;

/**
 * Declarative rule description for runtime rule-set updates.
 */
public record RuleDto(
        String id,
        String severity,
        String description,
        String type,
        Map<String, Object> params
) {
    public RuleDescriptor toDescriptor() {
        return new RuleDescriptor(id, severity, description, type, params);
    }
}
