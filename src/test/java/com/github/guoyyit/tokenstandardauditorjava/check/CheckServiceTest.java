package com.github.guoyyit.tokenstandardauditorjava.check;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.guoyyit.tokenstandardauditorjava.fixtures.AbiBuilder;
import com.github.guoyyit.tokenstandardauditorjava.fixtures.TestFixtures;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckRecord;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckRequest;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckStatus;
import com.github.guoyyit.tokenstandardauditorjava.model.ContractContract;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import com.github.guoyyit.tokenstandardauditorjava.parse.AbiParser;
import com.github.guoyyit.tokenstandardauditorjava.parse.BytecodeParser;
import com.github.guoyyit.tokenstandardauditorjava.rule.Rule;
import com.github.guoyyit.tokenstandardauditorjava.rule.RuleRegistry;
import com.github.guoyyit.tokenstandardauditorjava.rule.RuleSet;
import com.github.guoyyit.tokenstandardauditorjava.rule.impl.BuiltinRuleSets;
import com.github.guoyyit.tokenstandardauditorjava.store.CheckRecordStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckServiceTest {

    @TempDir
    Path tempDir;

    private CheckService service;
    private RuleRegistry registry;
    private CheckRecordStore store;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        registry = new RuleRegistry();
        registry.register(BuiltinRuleSets.erc20());
        registry.register(BuiltinRuleSets.erc721());
        store = new CheckRecordStore(tempDir.resolve("checks").toString(), objectMapper);
        store.init();
        service = new CheckService(registry, new AbiParser(objectMapper), new BytecodeParser(),
                store, 4, 60);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    private String erc20Abi() {
        return AbiBuilder.builder()
                .function("totalSupply()", "uint256")
                .function("balanceOf(address)", "uint256")
                .function("transfer(address,uint256)")
                .function("transferFrom(address,address,uint256)")
                .function("approve(address,uint256)")
                .function("allowance(address,address)", "uint256")
                .event("Transfer(address,address,uint256)", true, true, false)
                .event("Approval(address,address,uint256)", true, true, false)
                .build();
    }

    private String erc20Bytecode() {
        return TestFixtures.bytecodeOf(TestFixtures.erc20Functions(), TestFixtures.erc20Events());
    }

    @Test
    void submitCompletesWithReport() throws Exception {
        CheckRecord record = service.submit(new CheckRequest(erc20Abi(), erc20Bytecode(), "ERC20", null, null));
        assertNotNull(record.getId());
        assertTrue(record.getStatus() == CheckStatus.PENDING || record.getStatus() == CheckStatus.RUNNING
                || record.getStatus() == CheckStatus.COMPLETED);

        CheckRecord completed = awaitTerminal(record.getId(), 10);
        assertEquals(CheckStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getReport());
        assertEquals("ERC20", completed.getReport().standard());
        assertEquals("1.0", completed.getReport().version());
        assertTrue(completed.getReport().compliant());
    }

    @Test
    void defaultVersionIsDeterministic() throws Exception {
        CheckRecord record = service.submit(new CheckRequest(erc20Abi(), erc20Bytecode(), "ERC20", null, null));
        CheckRecord completed = awaitTerminal(record.getId(), 10);
        assertEquals("1.0", completed.getVersion());
    }

    @Test
    void sameContentIsCached() throws Exception {
        CheckRecord first = service.submit(new CheckRequest(erc20Abi(), erc20Bytecode(), "ERC20", null, null));
        awaitTerminal(first.getId(), 10);

        CheckRecord second = service.submit(new CheckRequest(erc20Abi(), erc20Bytecode(), "ERC20", null, null));
        CheckRecord completed = awaitTerminal(second.getId(), 10);
        assertEquals(CheckStatus.COMPLETED, completed.getStatus());
        assertTrue(completed.isCached());
        assertEquals(first.getId(), completed.getCachedFrom());
        assertEquals(first.getReport().findings(), completed.getReport().findings());
    }

    @Test
    void contentChangeRecomputes() throws Exception {
        CheckRecord first = service.submit(new CheckRequest(erc20Abi(), erc20Bytecode(), "ERC20", null, null));
        awaitTerminal(first.getId(), 10);

        List<String> functions = new ArrayList<>(TestFixtures.erc20Functions());
        functions.remove("transfer(address,uint256)");
        String differentBytecode = TestFixtures.bytecodeOf(functions, TestFixtures.erc20Events());
        CheckRecord second = service.submit(new CheckRequest(erc20Abi(), differentBytecode, "ERC20", null, null));
        CheckRecord completed = awaitTerminal(second.getId(), 10);
        assertFalse(completed.isCached());
        assertFalse(completed.getReport().compliant());
    }

    @Test
    void concurrentSubmissionsShareOneComputation() throws Exception {
        AtomicInteger evaluations = new AtomicInteger();
        Rule countingRule = new Rule() {
            public String id() { return "COUNT-001"; }
            public Severity severity() { return Severity.INFO; }
            public String description() { return "counts evaluations"; }
            public List<Finding> evaluate(ContractContract c) {
                evaluations.incrementAndGet();
                return List.of();
            }
        };
        RuleSet countSet = new RuleSet("COUNT", "1.0", "counting", List.of(countingRule));
        registry.register(countSet);

        int n = 8;
        List<CheckRecord> records = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            records.add(service.submit(new CheckRequest(erc20Abi(), erc20Bytecode(), "COUNT", null, null)));
        }
        for (CheckRecord r : records) {
            awaitTerminal(r.getId(), 10);
        }
        long distinctReports = records.stream()
                .map(r -> r.getReport().findings())
                .distinct()
                .count();
        assertEquals(1, distinctReports);
    }

    @Test
    void cancelLeavesNoReport() throws Exception {
        CountDownLatch blocker = new CountDownLatch(1);
        Rule blockingRule = new Rule() {
            public String id() { return "BLOCK-001"; }
            public Severity severity() { return Severity.ERROR; }
            public String description() { return "blocks"; }
            public List<Finding> evaluate(ContractContract c) {
                try { blocker.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return List.of();
            }
        };
        registry.register(new RuleSet("BLOCK", "1.0", "blocking", List.of(blockingRule)));

        CheckRecord record = service.submit(new CheckRequest(erc20Abi(), erc20Bytecode(), "BLOCK", null, null));
        Thread.sleep(200);
        CheckRecord cancelled = service.cancel(record.getId());
        assertEquals(CheckStatus.CANCELLED, cancelled.getStatus());
        assertNull(cancelled.getReport());

        blocker.countDown();
        CheckRecord completed = awaitTerminal(record.getId(), 10);
        assertEquals(CheckStatus.CANCELLED, completed.getStatus());
        assertNull(completed.getReport());
    }

    @Test
    void timeoutLeavesNoReport() throws Exception {
        CountDownLatch blocker = new CountDownLatch(1);
        Rule blockingRule = new Rule() {
            public String id() { return "BLOCK-001"; }
            public Severity severity() { return Severity.ERROR; }
            public String description() { return "blocks"; }
            public List<Finding> evaluate(ContractContract c) {
                try { blocker.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return List.of();
            }
        };
        registry.register(new RuleSet("BLOCK", "1.0", "blocking", List.of(blockingRule)));

        CheckRecord record = service.submit(new CheckRequest(erc20Abi(), erc20Bytecode(), "BLOCK", null, 1L));
        CheckRecord timedOut = awaitTerminal(record.getId(), 10);
        assertEquals(CheckStatus.TIMED_OUT, timedOut.getStatus());
        assertNull(timedOut.getReport());

        blocker.countDown();
    }

    @Test
    void completedCheckUnaffectedByCancel() throws Exception {
        CheckRecord record = service.submit(new CheckRequest(erc20Abi(), erc20Bytecode(), "ERC20", null, null));
        awaitTerminal(record.getId(), 10);
        CheckRecord cancelled = service.cancel(record.getId());
        assertEquals(CheckStatus.COMPLETED, cancelled.getStatus());
        assertNotNull(cancelled.getReport());
    }

    @Test
    void unknownStandardFailsWithError() throws Exception {
        CheckRecord record = service.submit(new CheckRequest(erc20Abi(), erc20Bytecode(), "NOPE", null, null));
        CheckRecord failed = awaitTerminal(record.getId(), 10);
        assertEquals(CheckStatus.FAILED, failed.getStatus());
        assertNotNull(failed.getErrorCode());
        assertNull(failed.getReport());
    }

    @Test
    void parseErrorFailsWithDistinguishableError() throws Exception {
        CheckRecord record = service.submit(new CheckRequest("{bad", erc20Bytecode(), "ERC20", null, null));
        CheckRecord failed = awaitTerminal(record.getId(), 10);
        assertEquals(CheckStatus.FAILED, failed.getStatus());
        assertEquals("ABI_PARSE_ERROR", failed.getErrorCode());
        assertNull(failed.getReport());
    }

    private CheckRecord awaitTerminal(String id, int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        CheckRecord record;
        do {
            record = service.get(id);
            if (record.getStatus().isTerminal()) {
                return record;
            }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Check " + id + " did not reach terminal state, last status: " + record.getStatus());
    }
}
