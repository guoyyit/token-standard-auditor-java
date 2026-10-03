package com.github.guoyyit.tokenstandardauditorjava.rule;

import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import com.github.guoyyit.tokenstandardauditorjava.rule.impl.AbiBytecodeConsistencyRule;
import com.github.guoyyit.tokenstandardauditorjava.rule.impl.EventIndexingRule;
import com.github.guoyyit.tokenstandardauditorjava.rule.impl.FunctionMutabilityRule;
import com.github.guoyyit.tokenstandardauditorjava.rule.impl.FunctionOutputRule;
import com.github.guoyyit.tokenstandardauditorjava.rule.impl.OptionalFunctionRule;
import com.github.guoyyit.tokenstandardauditorjava.rule.impl.RequiredEventRule;
import com.github.guoyyit.tokenstandardauditorjava.rule.impl.RequiredFunctionRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link Rule} instances from declarative {@link RuleDescriptor}s.
 *
 * <p>Unknown rule types, severities or missing parameters are reported via
 * {@link RuleConfigurationException}.
 */
@Component
public class RuleDescriptorParser {

    /**
     * Builds a rule set from the given descriptors.
     */
    public RuleSet parse(String standard, String version, String name, List<RuleDescriptor> descriptors) {
        List<Rule> rules = new ArrayList<>();
        for (RuleDescriptor descriptor : descriptors) {
            rules.add(parse(standard, version, descriptor));
        }
        return new RuleSet(standard, version, name, rules);
    }

    private Rule parse(String standard, String version, RuleDescriptor d) {
        if (d.id() == null || d.id().isBlank()) {
            throw new RuleConfigurationException("Rule id must not be empty");
        }
        Severity severity = parseSeverity(d.severity());
        String description = d.description() != null ? d.description() : "";
        String type = d.type();
        if (type == null) {
            throw new RuleConfigurationException("Rule type must not be empty for rule " + d.id());
        }
        return switch (type) {
            case "REQUIRED_FUNCTION" -> new RequiredFunctionRule(d.id(), severity, description, standard, version,
                    requireParam(d, "signature"));
            case "REQUIRED_EVENT" -> new RequiredEventRule(d.id(), severity, description, standard, version,
                    requireParam(d, "signature"), requireIndexed(d));
            case "FUNCTION_OUTPUT" -> new FunctionOutputRule(d.id(), severity, description, standard, version,
                    requireParam(d, "signature"), requireStringList(d, "outputs"));
            case "FUNCTION_MUTABILITY" -> new FunctionMutabilityRule(d.id(), severity, description, standard, version,
                    requireParam(d, "signature"), requireParam(d, "mutability"));
            case "ABI_BYTECODE_CONSISTENCY" -> new AbiBytecodeConsistencyRule(d.id(), severity, description,
                    standard, version);
            case "OPTIONAL_FUNCTION" -> new OptionalFunctionRule(d.id(), severity, description, standard, version,
                    requireParam(d, "signature"));
            case "EVENT_INDEXING" -> new EventIndexingRule(d.id(), severity, description, standard, version,
                    requireParam(d, "signature"), requireIndexed(d));
            default -> throw new RuleConfigurationException("Unknown rule type: " + type + " for rule " + d.id());
        };
    }

    private Severity parseSeverity(String severity) {
        if (severity == null) {
            throw new RuleConfigurationException("Rule severity must not be empty");
        }
        try {
            return Severity.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuleConfigurationException("Unknown severity: " + severity);
        }
    }

    private String requireParam(RuleDescriptor d, String name) {
        Object value = d.params() == null ? null : d.params().get(name);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new RuleConfigurationException("Rule " + d.id() + " is missing parameter '" + name + "'");
        }
        return s;
    }

    private List<String> requireStringList(RuleDescriptor d, String name) {
        Object value = d.params() == null ? null : d.params().get(name);
        if (!(value instanceof List<?> list)) {
            throw new RuleConfigurationException("Rule " + d.id() + " is missing parameter '" + name + "'");
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String s)) {
                throw new RuleConfigurationException("Rule " + d.id() + " parameter '" + name + "' must be a list of strings");
            }
            result.add(s);
        }
        return result;
    }

    private List<Boolean> requireIndexed(RuleDescriptor d) {
        Object value = d.params() == null ? null : d.params().get("indexed");
        if (!(value instanceof List<?> list)) {
            throw new RuleConfigurationException("Rule " + d.id() + " is missing parameter 'indexed'");
        }
        List<Boolean> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Boolean b)) {
                throw new RuleConfigurationException("Rule " + d.id() + " parameter 'indexed' must be a list of booleans");
            }
            result.add(b);
        }
        return result;
    }

    /**
     * Thrown when a rule descriptor is invalid.
     */
    public static class RuleConfigurationException extends RuntimeException {
        public RuleConfigurationException(String message) {
            super(message);
        }
    }
}
