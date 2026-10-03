package com.github.guoyyit.tokenstandardauditorjava.engine;

import com.github.guoyyit.tokenstandardauditorjava.api.AuditException;
import com.github.guoyyit.tokenstandardauditorjava.api.ContractSubmission;
import com.github.guoyyit.tokenstandardauditorjava.api.ErrorCode;
import com.github.guoyyit.tokenstandardauditorjava.domain.AuditReport;
import com.github.guoyyit.tokenstandardauditorjava.domain.CheckStatus;
import com.github.guoyyit.tokenstandardauditorjava.domain.Finding;
import com.github.guoyyit.tokenstandardauditorjava.domain.Severity;
import com.github.guoyyit.tokenstandardauditorjava.domain.TokenStandard;
import com.github.guoyyit.tokenstandardauditorjava.rules.RuleDefinition;
import com.github.guoyyit.tokenstandardauditorjava.rules.RuleSetRegistry;
import com.github.guoyyit.tokenstandardauditorjava.store.ReportStore;
import com.github.guoyyit.tokenstandardauditorjava.tests.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "auditor.check-timeout-ms=30000",
        "auditor.executor-core-size=4",
        "auditor.executor-max-size=8",
        "auditor.executor-queue-capacity=200"
})
class AuditServiceTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("auditor.data-dir", () -> TEST_DATA_DIR);
    }

    private static final String TEST_DATA_DIR =
            System.getProperty("java.io.tmpdir") + "/auditor-test-" + UUID.randomUUID();

    @Autowired
    private AuditService auditService;

    @Autowired
    private RuleSetRegistry ruleSetRegistry;

    @Autowired
    private ReportStore reportStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM checks");
        auditService.setExecutionHook(null);
    }

    @AfterEach
    void resetHook() {
        auditService.setExecutionHook(null);
    }

    private static ContractSubmission erc20Compliant() {
        return new ContractSubmission("c1", TestFixtures.ERC20_ABI, TestFixtures.erc20Bytecode(),
                TokenStandard.ERC20, null);
    }

    private AuditReport awaitTerminal(String checkId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            Optional<AuditReport> report = auditService.getReport(checkId);
            if (report.isPresent()) {
                return report.get();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("check " + checkId + " did not reach a terminal state in time");
    }

    @Test
    void compliantContractCompletesWithNoErrors() throws Exception {
        String checkId = auditService.submit(erc20Compliant());
        AuditReport report = awaitTerminal(checkId);
        assertEquals(CheckStatus.COMPLETED, report.getStatus());
        assertEquals("v1", report.getStandardVersion());
        assertTrue(report.getFindings().stream().noneMatch(f -> f.getSeverity() == Severity.ERROR),
                "findings: " + report.getFindings());
    }

    @Test
    void abiBytecodeMismatchIsReported() throws Exception {
        ContractSubmission submission = new ContractSubmission("c2", TestFixtures.ERC20_ABI,
                TestFixtures.erc20BytecodeWithout("transfer(address,uint256)"),
                TokenStandard.ERC20, null);
        AuditReport report = awaitTerminal(auditService.submit(submission));
        assertEquals(CheckStatus.COMPLETED, report.getStatus());
        assertTrue(report.getFindings().stream().anyMatch(f -> f.getSeverity() == Severity.ERROR
                && f.getMessage().contains("transfer(address,uint256)")
                && f.getMessage().contains("not present in the bytecode")),
                "findings: " + report.getFindings());
    }

    @Test
    void invalidAbiFailsWithDistinctError() throws Exception {
        ContractSubmission submission = new ContractSubmission("c3", "{broken",
                TestFixtures.erc20Bytecode(), TokenStandard.ERC20, null);
        AuditReport report = awaitTerminal(auditService.submit(submission));
        assertEquals(CheckStatus.FAILED, report.getStatus());
        assertTrue(report.getErrorMessage().contains("JSON"));
    }

    @Test
    void invalidBytecodeFailsWithDistinctError() throws Exception {
        ContractSubmission submission = new ContractSubmission("c4", TestFixtures.ERC20_ABI,
                "0x123", TokenStandard.ERC20, null);
        AuditReport report = awaitTerminal(auditService.submit(submission));
        assertEquals(CheckStatus.FAILED, report.getStatus());
        assertTrue(report.getErrorMessage().contains("odd length"));
    }

    @Test
    void unknownStandardVersionIsRejected() {
        ContractSubmission submission = new ContractSubmission("c5", TestFixtures.ERC20_ABI,
                TestFixtures.erc20Bytecode(), TokenStandard.ERC20, "v99");
        AuditException e = assertThrows(AuditException.class, () -> auditService.submit(submission));
        assertEquals(ErrorCode.UNKNOWN_STANDARD_VERSION, e.getCode());
    }

    @Test
    void undeclaredVersionResolvesToDefaultDeterministically() throws Exception {
        String first = auditService.submit(erc20Compliant());
        String second = auditService.submit(erc20Compliant());
        assertEquals("v1", awaitTerminal(first).getStandardVersion());
        assertEquals("v1", awaitTerminal(second).getStandardVersion());
    }

    @Test
    void identicalContentIsServedFromCacheWithIdenticalFindings() throws Exception {
        ContractSubmission submission = new ContractSubmission("c6", TestFixtures.ERC20_ABI,
                TestFixtures.erc20BytecodeWithout("approve(address,uint256)"),
                TokenStandard.ERC20, null);
        AuditReport first = awaitTerminal(auditService.submit(submission));
        AuditReport second = awaitTerminal(auditService.submit(submission));
        assertEquals(CheckStatus.COMPLETED, first.getStatus());
        assertEquals(CheckStatus.COMPLETED, second.getStatus());
        assertNotEquals(first.getCheckId(), second.getCheckId());
        assertEquals(first.getFindings().stream().map(Finding::getRuleId).toList(),
                second.getFindings().stream().map(Finding::getRuleId).toList());
        assertEquals(first.getFindings().stream().map(Finding::getMessage).toList(),
                second.getFindings().stream().map(Finding::getMessage).toList());
    }

    @Test
    void concurrentBatchSubmissionsAllCompleteConsistently() throws Exception {
        int threads = 8;
        int perThread = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<List<String>>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                List<ContractSubmission> batch = new ArrayList<>();
                for (int i = 0; i < perThread; i++) {
                    batch.add(erc20Compliant());
                }
                return auditService.submitBatch(batch);
            }));
        }
        List<String> allIds = new ArrayList<>();
        for (Future<List<String>> future : futures) {
            allIds.addAll(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertEquals(threads * perThread, allIds.size());
        List<List<String>> findingShapes = new ArrayList<>();
        for (String id : allIds) {
            AuditReport report = awaitTerminal(id);
            assertEquals(CheckStatus.COMPLETED, report.getStatus());
            findingShapes.add(report.getFindings().stream()
                    .map(f -> f.getRuleId() + "|" + f.getSeverity() + "|" + f.getMessage()).toList());
        }
        for (List<String> shape : findingShapes) {
            assertEquals(findingShapes.get(0), shape,
                    "identical content must yield identical conclusions");
        }
    }

    @Test
    void cancelledCheckLeavesNoPartialReport() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        auditService.setExecutionHook(() -> {
            entered.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        String checkId = auditService.submit(erc20Compliant());
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertTrue(auditService.cancel(checkId));
        AuditReport report = awaitTerminal(checkId);
        assertEquals(CheckStatus.CANCELLED, report.getStatus());
        assertTrue(report.getFindings().isEmpty());
        // completed checks are unaffected by cancel
        org.junit.jupiter.api.Assertions.assertFalse(auditService.cancel(checkId));
    }

    @Test
    void ruleUpdateAppliesToNewChecksButNotInFlightOnes() throws Exception {
        // contract without name() in ABI: v1 says optional -> WARNING
        String abiNoName = TestFixtures.ERC20_ABI.replace(
                "{\"type\":\"function\",\"name\":\"name\",\"stateMutability\":\"view\",\"inputs\":[],\"outputs\":[{\"name\":\"\",\"type\":\"string\"}]},",
                "");
        ContractSubmission submission = new ContractSubmission("c7", abiNoName,
                TestFixtures.erc20Bytecode(), TokenStandard.ERC20, null);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        auditService.setExecutionHook(() -> {
            entered.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        String inFlightId = auditService.submit(submission);
        assertTrue(entered.await(10, TimeUnit.SECONDS));

        // update rules while the check is in flight: name() becomes required ERROR
        RuleDefinition current = ruleSetRegistry.find(TokenStandard.ERC20, "v1").orElseThrow();
        List<RuleDefinition.FunctionRule> updatedFunctions = current.functions().stream()
                .map(f -> f.name().equals("name")
                        ? new RuleDefinition.FunctionRule(f.name(), f.inputs(), f.outputs(),
                        f.stateMutability(), true, Severity.ERROR)
                        : f)
                .toList();
        ruleSetRegistry.upsert(new RuleDefinition(current.id(), current.standard(),
                current.version(), current.defaultVersion(), updatedFunctions, current.events()));

        release.countDown();
        AuditReport inFlightReport = awaitTerminal(inFlightId);
        assertTrue(inFlightReport.getFindings().stream().anyMatch(f ->
                        f.getRuleId().equals("function:name()") && f.getSeverity() == Severity.WARNING),
                "in-flight check must use the rule snapshot from submission time: "
                        + inFlightReport.getFindings());

        AuditReport afterUpdate = awaitTerminal(auditService.submit(submission));
        assertTrue(afterUpdate.getFindings().stream().anyMatch(f ->
                        f.getRuleId().equals("function:name()") && f.getSeverity() == Severity.ERROR),
                "new checks must use updated rules: " + afterUpdate.getFindings());
    }

    @Test
    void crashRecoveryDiscardsIncompleteAndKeepsCompleted() throws Exception {
        AuditReport completed = awaitTerminal(auditService.submit(erc20Compliant()));
        reportStore.createPending("orphan-check", "some-key", TokenStandard.ERC20, "v1",
                "{}", Instant.now());
        assertEquals(Optional.of(CheckStatus.PENDING), auditService.getStatus("orphan-check"));

        auditService.recoverFromCrash();

        assertEquals(Optional.empty(), auditService.getStatus("orphan-check"),
                "incomplete checks must be discarded on recovery");
        assertTrue(auditService.getReport(completed.getCheckId()).isPresent(),
                "completed reports must survive recovery");
    }
}
