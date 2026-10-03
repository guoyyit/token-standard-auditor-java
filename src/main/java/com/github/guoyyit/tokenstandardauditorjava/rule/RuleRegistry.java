package com.github.guoyyit.tokenstandardauditorjava.rule;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of versioned rule sets.
 *
 * <p>Rule sets can be registered and replaced at runtime. Updates do not affect in-flight checks:
 * each check takes a {@link #snapshot(String, String) snapshot} of the rule set at submission time.
 */
@Component
public class RuleRegistry {

    private final Map<String, RuleSet> sets = new ConcurrentHashMap<>();
    private final Map<String, String> defaultVersions = new ConcurrentHashMap<>();

    /**
     * Registers (or replaces) a rule set.
     */
    public void register(RuleSet ruleSet) {
        sets.put(key(ruleSet.standard(), ruleSet.version()), ruleSet);
        defaultVersions.putIfAbsent(ruleSet.standard(), ruleSet.version());
    }

    /**
     * Sets the default version for a standard.
     */
    public void setDefaultVersion(String standard, String version) {
        defaultVersions.put(standard, version);
    }

    /**
     * Returns a snapshot of the rule set for the given standard and version.
     *
     * @param standard target standard
     * @param version  target version; if null, the default version is used
     * @return a copy of the rule set
     * @throws UnknownStandardException if the standard is not registered
     * @throws UnknownVersionException  if the version is not registered
     */
    public RuleSet snapshot(String standard, String version) {
        if (standard == null || standard.isBlank()) {
            throw new UnknownStandardException("Standard must not be empty");
        }
        String resolvedVersion = version != null ? version : defaultVersions.get(standard);
        if (resolvedVersion == null) {
            throw new UnknownStandardException("Unknown standard: " + standard);
        }
        RuleSet set = sets.get(key(standard, resolvedVersion));
        if (set == null) {
            throw new UnknownVersionException("Unknown version " + resolvedVersion + " for standard " + standard);
        }
        return new RuleSet(set.standard(), set.version(), set.name(), List.copyOf(set.rules()));
    }

    public Set<String> standards() {
        return defaultVersions.keySet();
    }

    public String defaultVersion(String standard) {
        String version = defaultVersions.get(standard);
        if (version == null) {
            throw new UnknownStandardException("Unknown standard: " + standard);
        }
        return version;
    }

    public List<RuleSet> list() {
        return sets.values().stream()
                .sorted(Comparator.comparing(RuleSet::standard).thenComparing(RuleSet::version))
                .toList();
    }

    public Optional<RuleSet> find(String standard, String version) {
        return Optional.ofNullable(sets.get(key(standard, version)));
    }

    private static String key(String standard, String version) {
        return standard + "|" + version;
    }

    /**
     * Thrown when a requested standard is not registered.
     */
    public static class UnknownStandardException extends RuntimeException {
        public UnknownStandardException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when a requested version is not registered.
     */
    public static class UnknownVersionException extends RuntimeException {
        public UnknownVersionException(String message) {
            super(message);
        }
    }
}
