package com.github.guoyyit.tokenstandardauditorjava.rule;

import java.util.List;

/**
 * A versioned set of rules for a token standard.
 *
 * <p>The rule list is defensively copied so that in-flight checks are unaffected by later updates
 * to the registry.
 *
 * @param standard standard name, e.g. "ERC20"
 * @param version  rule-set version, e.g. "1.0"
 * @param name     human-readable name, e.g. "ERC-20 Fungible Token Standard"
 * @param rules    ordered list of rules
 */
public record RuleSet(
        String standard,
        String version,
        String name,
        List<Rule> rules
) {
    public RuleSet {
        rules = List.copyOf(rules);
    }
}
