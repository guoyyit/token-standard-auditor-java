package com.github.guoyyit.tokenstandardauditorjava.check;

import com.github.guoyyit.tokenstandardauditorjava.common.Sha256;
import com.github.guoyyit.tokenstandardauditorjava.model.BytecodeInfo;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckRecord;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckReport;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckRequest;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckStatus;
import com.github.guoyyit.tokenstandardauditorjava.model.ContractContract;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.parse.AbiParser;
import com.github.guoyyit.tokenstandardauditorjava.parse.BytecodeParser;
import com.github.guoyyit.tokenstandardauditorjava.parse.ParseException;
import com.github.guoyyit.tokenstandardauditorjava.rule.Rule;
import com.github.guoyyit.tokenstandardauditorjava.rule.RuleRegistry;
import com.github.guoyyit.tokenstandardauditorjava.rule.RuleSet;
import com.github.guoyyit.tokenstandardauditorjava.store.CheckRecordStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates compliance checks: parsing, rule evaluation, caching, concurrency, cancellation and
 * timeout.
 *
 * <h2>Rule selection</h2>
 * <p>Each check snapshots the rule set for the requested standard and version at submission time.
 * Later rule updates do not affect in-flight checks.
 *
 * <h2>Caching</h2>
 * <p>Results are cached by contract content (standard + version + ABI hash + bytecode hash).
 * Submitting the same content again returns the cached report without re-evaluation. Concurrent
 * submissions of the same content share a single computation and therefore always observe the same
 * fresh result.
 *
 * <h2>Cancellation and timeout</h2>
 * <p>A cancelled or timed-out check ends in a terminal state with no report. Completed checks are
 * unaffected by cancellation.
 */
@Service
public class CheckService {

    private static final Logger log = LoggerFactory.getLogger(CheckService.class);

    private final RuleRegistry ruleRegistry;
    private final AbiParser abiParser;
    private final BytecodeParser bytecodeParser;
    private final CheckRecordStore store;
    private final long defaultTimeoutSeconds;

    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;

    /** Completed reports keyed by content fingerprint. */
    private final Map<String, CachedReport> cache = new ConcurrentHashMap<>();

    public CheckService(RuleRegistry ruleRegistry, AbiParser abiParser, BytecodeParser bytecodeParser,
                        CheckRecordStore store,
                        @Value("${auditor.check.threads:0}") int threads,
                        @Value("${auditor.check.timeout-seconds:60}") long defaultTimeoutSeconds) {
        this.ruleRegistry = ruleRegistry;
        this.abiParser = abiParser;
        this.bytecodeParser = bytecodeParser;
        this.store = store;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        int poolSize = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "check-worker");
            t.setDaemon(true);
            return t;
        });
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "check-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Submits a single check.
     */
    public CheckRecord submit(CheckRequest request) {
        String abiHash = Sha256.hex(request.abi());
        String bytecodeHash = Sha256.hex(request.bytecode());

        CheckRecord record = new CheckRecord(UUID.randomUUID().toString(), CheckStatus.PENDING,
                request.standard(), request.version(), abiHash, bytecodeHash, Instant.now());
        store.save(record);

        RuleSet ruleSet;
        try {
            ruleSet = ruleRegistry.snapshot(request.standard(), request.version());
        } catch (RuleRegistry.UnknownStandardException | RuleRegistry.UnknownVersionException e) {
            fail(record, e instanceof RuleRegistry.UnknownStandardException ? "UNKNOWN_STANDARD" : "UNKNOWN_VERSION",
                    e.getMessage());
            store.save(record);
            return record;
        }
        String standard = ruleSet.standard();
        String version = ruleSet.version();
        record.setStandard(standard);
        record.setVersion(version);
        store.save(record);

        String contentKey = standard + "|" + version + "|" + abiHash + "|" + bytecodeHash;

        CachedReport cached = cache.get(contentKey);
        if (cached != null) {
            complete(record, cached.report(), true, cached.originalCheckId());
            store.save(record);
            log.debug("Check {} served from cache", record.getId());
            return record;
        }

        executor.submit(() -> {
            try {
                runCheck(request, ruleSet, abiHash, bytecodeHash, contentKey, record);
            } catch (Exception e) {
                log.error("Check {} failed", record.getId(), e);
            }
        });

        long timeout = request.timeoutSeconds() != null && request.timeoutSeconds() > 0
                ? request.timeoutSeconds() : defaultTimeoutSeconds;
        scheduler.schedule(() -> {
            synchronized (record) {
                if (record.getStatus().isTerminal()) {
                    return;
                }
                record.setStatus(CheckStatus.TIMED_OUT);
                record.setCompletedAt(Instant.now());
                store.save(record);
                log.debug("Check {} timed out", record.getId());
            }
        }, timeout, TimeUnit.SECONDS);

        return record;
    }

    /**
     * Submits a batch of checks.
     */
    public List<CheckRecord> submitBatch(List<CheckRequest> requests) {
        List<CheckRecord> records = new ArrayList<>();
        for (CheckRequest request : requests) {
            records.add(submit(request));
        }
        return records;
    }

    /**
     * Cancels a check. Completed checks are unaffected.
     */
    public CheckRecord cancel(String id) {
        CheckRecord record = store.load(id)
                .orElseThrow(() -> new CheckNotFoundException("Check not found: " + id));
        synchronized (record) {
            if (record.getStatus().isTerminal()) {
                return record;
            }
            record.setStatus(CheckStatus.CANCELLED);
            record.setCompletedAt(Instant.now());
            store.save(record);
        }
        return record;
    }

    /**
     * Returns the check with the given id.
     */
    public CheckRecord get(String id) {
        CheckRecord record = store.load(id)
                .orElseThrow(() -> new CheckNotFoundException("Check not found: " + id));
        return record;
    }

    /**
     * Returns all checks, ordered by submission time.
     */
    public List<CheckRecord> list() {
        return store.list().stream()
                .sorted(Comparator.comparing(CheckRecord::getSubmittedAt))
                .toList();
    }

    private void runCheck(CheckRequest request, RuleSet ruleSet, String abiHash, String bytecodeHash,
                          String contentKey, CheckRecord record) {
        try {
            ParsedContract parsed = parse(request);
            List<Finding> findings = new ArrayList<>();
            for (Rule rule : ruleSet.rules()) {
                findings.addAll(rule.evaluate(parsed.contract()));
            }
            findings.sort(Comparator.comparing(Finding::ruleId));
            CheckReport report = buildReport(null, ruleSet.standard(), ruleSet.version(),
                    abiHash, bytecodeHash, findings);
            synchronized (record) {
                if (record.getStatus().isTerminal()) {
                    return;
                }
                complete(record, report, false, null);
                store.save(record);
            }
            cache.put(contentKey, new CachedReport(record.getReport(), record.getId()));
        } catch (ParseException e) {
            synchronized (record) {
                if (record.getStatus().isTerminal()) {
                    return;
                }
                fail(record, e.getCode(), e.getMessage());
                store.save(record);
            }
        } catch (Exception e) {
            log.error("Check computation failed", e);
            synchronized (record) {
                if (record.getStatus().isTerminal()) {
                    return;
                }
                fail(record, "CHECK_ERROR", e.getMessage());
                store.save(record);
            }
        }
    }

    private ParsedContract parse(CheckRequest request) {
        AbiParser.ParsedAbi parsedAbi = abiParser.parse(request.abi());
        BytecodeInfo bytecode = bytecodeParser.parse(request.bytecode());
        ContractContract contract = new ContractContract(parsedAbi.functions(), parsedAbi.events(), bytecode);
        return new ParsedContract(contract);
    }

    private CheckReport buildReport(String checkId, String standard, String version,
                                    String abiHash, String bytecodeHash, List<Finding> findings) {
        int errors = (int) findings.stream().filter(f -> f.severity().name().equals("ERROR")).count();
        int warnings = (int) findings.stream().filter(f -> f.severity().name().equals("WARNING")).count();
        int infos = (int) findings.stream().filter(f -> f.severity().name().equals("INFO")).count();
        return new CheckReport(checkId, standard, version, abiHash, bytecodeHash, findings,
                errors, warnings, infos, errors == 0, Instant.now());
    }

    private void complete(CheckRecord record, CheckReport report, boolean cached, String cachedFrom) {
        CheckReport finalReport = cached ? report : buildReport(record.getId(), report.standard(),
                report.version(), report.abiHash(), report.bytecodeHash(), report.findings());
        record.setReport(finalReport);
        record.setStatus(CheckStatus.COMPLETED);
        record.setCompletedAt(Instant.now());
        record.setCached(cached);
        record.setCachedFrom(cachedFrom);
    }

    private void fail(CheckRecord record, String code, String message) {
        record.setStatus(CheckStatus.FAILED);
        record.setErrorCode(code);
        record.setErrorMessage(message);
        record.setCompletedAt(Instant.now());
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        executor.shutdownNow();
    }

    private record ParsedContract(ContractContract contract) {
    }

    /**
     * A cached report and the id of the check that originally produced it.
     */
    private record CachedReport(CheckReport report, String originalCheckId) {
    }

    /**
     * Thrown when a check id is not found.
     */
    public static class CheckNotFoundException extends RuntimeException {
        public CheckNotFoundException(String message) {
            super(message);
        }
    }
}
