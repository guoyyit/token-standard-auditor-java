package com.github.guoyyit.tokenstandardauditorjava.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiModels.ContractAbi;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiParser;
import com.github.guoyyit.tokenstandardauditorjava.api.AuditException;
import com.github.guoyyit.tokenstandardauditorjava.api.ContractSubmission;
import com.github.guoyyit.tokenstandardauditorjava.api.ErrorCode;
import com.github.guoyyit.tokenstandardauditorjava.bytecode.EvmBytecodeParser;
import com.github.guoyyit.tokenstandardauditorjava.bytecode.ParsedBytecode;
import com.github.guoyyit.tokenstandardauditorjava.domain.AuditReport;
import com.github.guoyyit.tokenstandardauditorjava.domain.CheckStatus;
import com.github.guoyyit.tokenstandardauditorjava.domain.Finding;
import com.github.guoyyit.tokenstandardauditorjava.domain.TokenStandard;
import com.github.guoyyit.tokenstandardauditorjava.rules.RuleDefinition;
import com.github.guoyyit.tokenstandardauditorjava.rules.RuleSetRegistry;
import com.github.guoyyit.tokenstandardauditorjava.store.ReportStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry point for submitting, cancelling and querying audit checks.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AbiParser abiParser;
    private final EvmBytecodeParser bytecodeParser;
    private final RuleSetRegistry ruleSetRegistry;
    private final ComplianceEngine engine;
    private final ReportStore store;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long checkTimeoutMs;

    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "audit-timeout"));
    private final Map<String, CheckHandle> inFlight = new ConcurrentHashMap<>();

    /** Test seam: invoked at the start of each check execution. Null in production. */
    private volatile Runnable executionHook;

    public AuditService(AbiParser abiParser,
                        EvmBytecodeParser bytecodeParser,
                        RuleSetRegistry ruleSetRegistry,
                        ComplianceEngine engine,
                        ReportStore store,
                        @Value("${auditor.check-timeout-ms:30000}") long checkTimeoutMs,
                        @Value("${auditor.executor-core-size:4}") int coreSize,
                        @Value("${auditor.executor-max-size:8}") int maxSize,
                        @Value("${auditor.executor-queue-capacity:100}") int queueCapacity) {
        this.abiParser = abiParser;
        this.bytecodeParser = bytecodeParser;
        this.ruleSetRegistry = ruleSetRegistry;
        this.engine = engine;
        this.store = store;
        this.checkTimeoutMs = checkTimeoutMs;
        this.executor = new ThreadPoolExecutor(coreSize, maxSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> daemon(r, "audit-worker"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PostConstruct
    void recoverFromCrash() {
        int discarded = store.discardIncomplete();
        if (discarded > 0) {
            log.info("Crash recovery: discarded {} incomplete check(s)", discarded);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        scheduler.shutdownNow();
    }

    /** Submits one contract for checking; returns the check id. */
    public String submit(ContractSubmission submission) {
        RuleDefinition rules = resolveRules(submission);
        String checkId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        String contractKey = contractKey(submission, rules);

        store.createPending(checkId, contractKey, submission.standard(), rules.version(),
                requestJson(submission, rules.version()), createdAt);

        Optional<AuditReport> cached = store.findCompletedByContractKey(contractKey);
        if (cached.isPresent()) {
            AuditReport report = new AuditReport(checkId, contractKey, submission.standard(),
                    rules.version(), CheckStatus.COMPLETED, cached.get().getFindings(), null,
                    createdAt, Instant.now());
            store.saveTerminal(report, null);
            return checkId;
        }

        CheckHandle handle = new CheckHandle(checkId, contractKey, submission.standard(),
                rules.version(), createdAt);
        inFlight.put(checkId, handle);
        handle.future = executor.submit(() -> runCheck(handle, submission, rules));
        scheduler.schedule(() -> onTimeout(checkId), checkTimeoutMs, TimeUnit.MILLISECONDS);
        return checkId;
    }

    /** Submits a batch of contracts; returns check ids in submission order. */
    public List<String> submitBatch(List<ContractSubmission> submissions) {
        for (ContractSubmission submission : submissions) {
            resolveRules(submission);
        }
        List<String> ids = new ArrayList<>(submissions.size());
        for (ContractSubmission submission : submissions) {
            ids.add(submit(submission));
        }
        return ids;
    }

    /** Requests cancellation; completed checks are unaffected. */
    public boolean cancel(String checkId) {
        Optional<CheckStatus> status = store.findStatus(checkId);
        if (status.isEmpty() || isTerminal(status.get())) {
            return false;
        }
        CheckHandle handle = inFlight.get(checkId);
        if (handle == null) {
            return false;
        }
        handle.cancelRequested.set(true);
        Future<?> future = handle.future;
        if (future != null) {
            future.cancel(true);
        }
        persistTerminal(handle, CheckStatus.CANCELLED, ErrorCode.CANCELLED,
                "check was cancelled", List.of());
        return true;
    }

    /** Returns the report if the check finished (successfully or not). */
    public Optional<AuditReport> getReport(String checkId) {
        return store.findReport(checkId);
    }

    /** Returns the current status of a check, if known. */
    public Optional<CheckStatus> getStatus(String checkId) {
        return store.findStatus(checkId);
    }

    private void runCheck(CheckHandle handle, ContractSubmission submission, RuleDefinition rules) {
        try {
            store.markRunning(handle.checkId);
            Runnable hook = executionHook;
            if (hook != null) {
                hook.run();
            }
            checkPoint(handle);
            ContractAbi abi = abiParser.parse(submission.abiJson());
            checkPoint(handle);
            ParsedBytecode bytecode = bytecodeParser.parse(submission.bytecodeHex());
            checkPoint(handle);
            List<Finding> findings = engine.evaluate(rules, abi, bytecode);
            checkPoint(handle);
            persistTerminal(handle, CheckStatus.COMPLETED, null, null, findings);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (handle.timedOut.get()) {
                persistTerminal(handle, CheckStatus.TIMEOUT, ErrorCode.TIMEOUT,
                        "check timed out after " + checkTimeoutMs + " ms", List.of());
            } else {
                persistTerminal(handle, CheckStatus.CANCELLED, ErrorCode.CANCELLED,
                        "check was cancelled", List.of());
            }
        } catch (AuditException e) {
            persistTerminal(handle, CheckStatus.FAILED, e.getCode(), e.getMessage(), List.of());
        } catch (Exception e) {
            persistTerminal(handle, CheckStatus.FAILED, ErrorCode.INTERNAL_ERROR,
                    "unexpected error: " + e.getMessage(), List.of());
        } finally {
            handle.finished.set(true);
            inFlight.remove(handle.checkId);
        }
    }

    private void onTimeout(String checkId) {
        CheckHandle handle = inFlight.get(checkId);
        if (handle == null) {
            return;
        }
        handle.timedOut.set(true);
        Future<?> future = handle.future;
        if (future != null) {
            future.cancel(true);
        }
        persistTerminal(handle, CheckStatus.TIMEOUT, ErrorCode.TIMEOUT,
                "check timed out after " + checkTimeoutMs + " ms", List.of());
    }

    private void checkPoint(CheckHandle handle) throws InterruptedException {
        if (Thread.currentThread().isInterrupted() || handle.cancelRequested.get()
                || handle.timedOut.get()) {
            throw new InterruptedException();
        }
    }

    private void persistTerminal(CheckHandle handle, CheckStatus status, ErrorCode errorCode,
                                 String errorMessage, List<Finding> findings) {
        if (!handle.persisted.compareAndSet(false, true)) {
            return;
        }
        AuditReport report = new AuditReport(handle.checkId, handle.contractKey, handle.standard,
                handle.standardVersion, status, findings, errorMessage, handle.createdAt,
                Instant.now());
        try {
            store.saveTerminal(report, errorCode == null ? null : errorCode.name());
        } catch (Exception e) {
            log.error("failed to persist terminal report for check {}", handle.checkId, e);
        }
    }

    private RuleDefinition resolveRules(ContractSubmission submission) {
        TokenStandard standard = submission.standard();
        if (standard == null) {
            throw new AuditException(ErrorCode.UNKNOWN_STANDARD_VERSION, "standard is required");
        }
        String version = submission.standardVersion();
        Optional<RuleDefinition> rules = (version == null || version.isBlank())
                ? ruleSetRegistry.findDefault(standard)
                : ruleSetRegistry.find(standard, version);
        return rules.orElseThrow(() -> new AuditException(ErrorCode.UNKNOWN_STANDARD_VERSION,
                "no rule set registered for " + standard + " version " + version));
    }

    private String contractKey(ContractSubmission submission, RuleDefinition rules) {
        try {
            String rulesJson = objectMapper.writeValueAsString(rules);
            return sha256(submission.standard().name() + "|" + rules.version() + "|"
                    + sha256(rulesJson) + "|" + submission.abiJson() + "|" + submission.bytecodeHex());
        } catch (Exception e) {
            throw new AuditException(ErrorCode.INTERNAL_ERROR, "failed to compute contract key", e);
        }
    }

    private String requestJson(ContractSubmission submission, String resolvedVersion) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("contractId", submission.contractId());
            map.put("standard", submission.standard().name());
            map.put("standardVersion", resolvedVersion);
            map.put("abiJson", submission.abiJson());
            map.put("bytecodeHex", submission.bytecodeHex());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new AuditException(ErrorCode.INTERNAL_ERROR, "failed to serialize request", e);
        }
    }

    private static boolean isTerminal(CheckStatus status) {
        return status == CheckStatus.COMPLETED || status == CheckStatus.FAILED
                || status == CheckStatus.CANCELLED || status == CheckStatus.TIMEOUT;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16));
                sb.append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Thread daemon(Runnable r, String name) {
        Thread thread = new Thread(r, name);
        thread.setDaemon(true);
        return thread;
    }

    void setExecutionHook(Runnable hook) {
        this.executionHook = hook;
    }

    private static final class CheckHandle {
        private final String checkId;
        private final String contractKey;
        private final TokenStandard standard;
        private final String standardVersion;
        private final Instant createdAt;
        private final AtomicBoolean cancelRequested = new AtomicBoolean();
        private final AtomicBoolean timedOut = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean persisted = new AtomicBoolean();
        private volatile Future<?> future;

        private CheckHandle(String checkId, String contractKey, TokenStandard standard,
                            String standardVersion, Instant createdAt) {
            this.checkId = checkId;
            this.contractKey = contractKey;
            this.standard = standard;
            this.standardVersion = standardVersion;
            this.createdAt = createdAt;
        }
    }
}
