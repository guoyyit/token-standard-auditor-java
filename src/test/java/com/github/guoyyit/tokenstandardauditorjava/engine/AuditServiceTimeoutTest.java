package com.github.guoyyit.tokenstandardauditorjava.engine;

import com.github.guoyyit.tokenstandardauditorjava.api.ContractSubmission;
import com.github.guoyyit.tokenstandardauditorjava.domain.AuditReport;
import com.github.guoyyit.tokenstandardauditorjava.domain.CheckStatus;
import com.github.guoyyit.tokenstandardauditorjava.domain.TokenStandard;
import com.github.guoyyit.tokenstandardauditorjava.tests.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "auditor.check-timeout-ms=300",
        "auditor.executor-core-size=2",
        "auditor.executor-max-size=2",
        "auditor.executor-queue-capacity=50"
})
class AuditServiceTimeoutTest {

    private static final String TEST_DATA_DIR =
            System.getProperty("java.io.tmpdir") + "/auditor-timeout-test-" + UUID.randomUUID();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("auditor.data-dir", () -> TEST_DATA_DIR);
    }

    @Autowired
    private AuditService auditService;

    @AfterEach
    void resetHook() {
        auditService.setExecutionHook(null);
    }

    @Test
    void slowCheckTimesOutAndLeavesNoPartialReport() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        auditService.setExecutionHook(() -> {
            entered.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        String checkId = auditService.submit(new ContractSubmission("slow",
                TestFixtures.ERC20_ABI, TestFixtures.erc20Bytecode(), TokenStandard.ERC20, null));
        assertTrue(entered.await(10, TimeUnit.SECONDS));

        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        AuditReport report = null;
        while (Instant.now().isBefore(deadline)) {
            Optional<AuditReport> current = auditService.getReport(checkId);
            if (current.isPresent()) {
                report = current.get();
                break;
            }
            Thread.sleep(20);
        }
        org.junit.jupiter.api.Assertions.assertNotNull(report, "check did not reach terminal state");
        assertEquals(CheckStatus.TIMEOUT, report.getStatus());
        assertTrue(report.getFindings().isEmpty(), "timed-out check must not expose partial findings");
        assertTrue(report.getErrorMessage().contains("timed out"));
    }
}
