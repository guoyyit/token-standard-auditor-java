package com.github.guoyyit.tokenstandardauditorjava.api.dto;

import com.github.guoyyit.tokenstandardauditorjava.rule.RuleSet;

import java.util.List;

/**
 * A versioned rule set for a token standard.
 */
public record RuleSetDto(
        String standard,
        String version,
        String name,
        List<RuleDto> rules
) {
    public static RuleSetDto from(RuleSet set) {
        List<RuleDto> rules = set.rules().stream()
                .map(r -> new RuleDto(r.id(), r.severity().name(), r.description(), null, null))
                .toList();
        return new RuleSetDto(set.standard(), set.version(), set.name(), rules);
    }
}
