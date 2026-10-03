package com.github.guoyyit.tokenstandardauditorjava.config;

import com.github.guoyyit.tokenstandardauditorjava.rule.RuleRegistry;
import com.github.guoyyit.tokenstandardauditorjava.rule.impl.BuiltinRuleSets;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes the rule registry with the built-in rule sets.
 */
@Configuration
public class RuleRegistryConfig {

    private final RuleRegistry registry;

    public RuleRegistryConfig(RuleRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void init() {
        registry.register(BuiltinRuleSets.erc20());
        registry.register(BuiltinRuleSets.erc721());
        registry.register(BuiltinRuleSets.erc1155());
    }
}
