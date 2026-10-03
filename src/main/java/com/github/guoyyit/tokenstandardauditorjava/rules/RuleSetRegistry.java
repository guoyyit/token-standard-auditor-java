package com.github.guoyyit.tokenstandardauditorjava.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.tokenstandardauditorjava.domain.TokenStandard;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds all rule sets keyed by (standard, version). Updates are applied
 * atomically so in-flight checks keep using the snapshot they started with.
 */
@Component
public class RuleSetRegistry {

    private static final Logger log = LoggerFactory.getLogger(RuleSetRegistry.class);

    private record Key(TokenStandard standard, String version) {
    }

    private record Snapshot(Map<Key, RuleDefinition> rules, Map<TokenStandard, String> defaults) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of());

    @PostConstruct
    void loadBuiltinRules() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:rules/*.json");
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    RuleDefinition definition = objectMapper.readValue(in, RuleDefinition.class);
                    upsert(definition);
                    log.info("Loaded rule set {} for {} v{}", definition.id(),
                            definition.standard(), definition.version());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to load built-in rule sets", e);
        }
    }

    /** Returns the rule set for the exact (standard, version) pair. */
    public Optional<RuleDefinition> find(TokenStandard standard, String version) {
        return Optional.ofNullable(snapshot.rules().get(new Key(standard, version)));
    }

    /** Returns the default rule set for a standard when no version is declared. */
    public Optional<RuleDefinition> findDefault(TokenStandard standard) {
        Snapshot current = snapshot;
        String version = current.defaults().get(standard);
        if (version == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(current.rules().get(new Key(standard, version)));
    }

    /** Atomically replaces the rule set for (standard, version). */
    public synchronized void upsert(RuleDefinition definition) {
        Snapshot current = snapshot;
        Map<Key, RuleDefinition> rules = new HashMap<>(current.rules());
        Map<TokenStandard, String> defaults = new HashMap<>(current.defaults());
        rules.put(new Key(definition.standard(), definition.version()), definition);
        if (definition.defaultVersion() || !defaults.containsKey(definition.standard())) {
            defaults.put(definition.standard(), definition.version());
        }
        snapshot = new Snapshot(Map.copyOf(rules), Map.copyOf(defaults));
    }

    public List<RuleDefinition> listAll() {
        return new ArrayList<>(snapshot.rules().values());
    }
}
