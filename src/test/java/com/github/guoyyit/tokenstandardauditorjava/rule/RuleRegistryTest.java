package com.github.guoyyit.tokenstandardauditorjava.rule;

import com.github.guoyyit.tokenstandardauditorjava.model.ContractContract;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleRegistryTest {

    private RuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RuleRegistry();
    }

    @Test
    void snapshotIsDefensiveCopy() {
        Rule rule = new TestRule("R1", Severity.ERROR);
        registry.register(new RuleSet("T", "1.0", "test", List.of(rule)));

        RuleSet first = registry.snapshot("T", "1.0");
        RuleSet second = registry.snapshot("T", "1.0");
        assertEquals(1, first.rules().size());
        assertNotSame(first, second);
        // the snapshot is independent of later registry updates
        registry.register(new RuleSet("T", "1.0", "test", List.of(new TestRule("R2", Severity.WARNING))));
        assertEquals("R1", first.rules().get(0).id());
    }

    @Test
    void updateDoesNotAffectInFlightSnapshot() throws Exception {
        CountDownLatch blocker = new CountDownLatch(1);
        Rule blockingRule = new TestRule("BLOCK-1", Severity.ERROR) {
            @Override
            public List<Finding> evaluate(ContractContract contract) {
                try { blocker.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return List.of();
            }
        };
        registry.register(new RuleSet("T", "1.0", "test", List.of(blockingRule)));

        // take a snapshot (simulating an in-flight check)
        RuleSet inFlight = registry.snapshot("T", "1.0");
        assertEquals(1, inFlight.rules().size());

        // update the registry while the check is in flight
        Rule newRule = new TestRule("BLOCK-2", Severity.WARNING);
        registry.register(new RuleSet("T", "1.0", "test", List.of(newRule)));

        // the in-flight snapshot still holds the old rule
        assertEquals("BLOCK-1", inFlight.rules().get(0).id());
        assertEquals(Severity.ERROR, inFlight.rules().get(0).severity());

        blocker.countDown();
    }

    @Test
    void defaultVersionIsDeterministic() {
        registry.register(new RuleSet("T", "1.0", "test", List.of()));
        registry.register(new RuleSet("T", "2.0", "test-v2", List.of()));
        assertEquals("1.0", registry.defaultVersion("T"));
        // snapshot without version uses the default
        RuleSet snapshot = registry.snapshot("T", null);
        assertEquals("1.0", snapshot.version());
    }

    @Test
    void unknownStandardThrows() {
        assertThrows(RuleRegistry.UnknownStandardException.class, () -> registry.snapshot("NOPE", null));
    }

    @Test
    void unknownVersionThrows() {
        registry.register(new RuleSet("T", "1.0", "test", List.of()));
        assertThrows(RuleRegistry.UnknownVersionException.class, () -> registry.snapshot("T", "9.9"));
    }

    @Test
    void descriptorParserBuildsRules() {
        RuleDescriptorParser parser = new RuleDescriptorParser();
        List<RuleDescriptor> descriptors = List.of(
                new RuleDescriptor("R1", "ERROR", "desc", "REQUIRED_FUNCTION",
                        Map.of("signature", "transfer(address,uint256)")),
                new RuleDescriptor("R2", "WARNING", "desc", "FUNCTION_OUTPUT",
                        Map.of("signature", "transfer(address,uint256)", "outputs", List.of("bool"))));
        RuleSet set = parser.parse("T", "1.0", "test", descriptors);
        assertEquals(2, set.rules().size());
        assertEquals("R1", set.rules().get(0).id());
    }

    @Test
    void descriptorParserRejectsUnknownType() {
        RuleDescriptorParser parser = new RuleDescriptorParser();
        List<RuleDescriptor> descriptors = List.of(
                new RuleDescriptor("R1", "ERROR", "desc", "BOGUS", Map.of()));
        assertThrows(RuleDescriptorParser.RuleConfigurationException.class,
                () -> parser.parse("T", "1.0", "test", descriptors));
    }

    @Test
    void descriptorParserRejectsUnknownSeverity() {
        RuleDescriptorParser parser = new RuleDescriptorParser();
        List<RuleDescriptor> descriptors = List.of(
                new RuleDescriptor("R1", "BOGUS", "desc", "REQUIRED_FUNCTION",
                        Map.of("signature", "f()")));
        assertThrows(RuleDescriptorParser.RuleConfigurationException.class,
                () -> parser.parse("T", "1.0", "test", descriptors));
    }

    private static class TestRule implements Rule {
        private final String id;
        private final Severity severity;

        TestRule(String id, Severity severity) {
            this.id = id;
            this.severity = severity;
        }

        @Override
        public String id() { return id; }

        @Override
        public Severity severity() { return severity; }

        @Override
        public String description() { return "test rule"; }

        @Override
        public List<Finding> evaluate(ContractContract contract) { return List.of(); }
    }
}
