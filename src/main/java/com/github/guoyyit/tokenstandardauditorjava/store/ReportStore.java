package com.github.guoyyit.tokenstandardauditorjava.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.guoyyit.tokenstandardauditorjava.domain.AuditReport;
import com.github.guoyyit.tokenstandardauditorjava.domain.CheckStatus;
import com.github.guoyyit.tokenstandardauditorjava.domain.TokenStandard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Durable store for check records and completed reports.
 * Completed reports survive restarts; incomplete ones never become visible.
 */
@Repository
public class ReportStore {

    private static final Set<String> TERMINAL =
            Set.of("COMPLETED", "FAILED", "CANCELLED", "TIMEOUT");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ReportStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /** Creates a new PENDING check record. */
    public void createPending(String checkId, String contractKey, TokenStandard standard,
                              String standardVersion, String requestJson, Instant createdAt) {
        jdbc.update("INSERT INTO checks (check_id, contract_key, standard, standard_version, status,"
                        + " request_json, created_at) VALUES (?,?,?,?,?,?,?)",
                checkId, contractKey, standard.name(), standardVersion,
                CheckStatus.PENDING.name(), requestJson, createdAt.toString());
    }

    /** Marks a check as RUNNING. */
    public void markRunning(String checkId) {
        jdbc.update("UPDATE checks SET status=? WHERE check_id=? AND status=?",
                CheckStatus.RUNNING.name(), checkId, CheckStatus.PENDING.name());
    }

    /** Atomically persists a terminal-state report (COMPLETED/FAILED/CANCELLED/TIMEOUT). */
    public void saveTerminal(AuditReport report, String errorCode) {
        try {
            String reportJson = objectMapper.writeValueAsString(report);
            jdbc.update("UPDATE checks SET status=?, report_json=?, error_code=?, error_message=?,"
                            + " completed_at=? WHERE check_id=? AND status IN (?,?)",
                    report.getStatus().name(), reportJson, errorCode, report.getErrorMessage(),
                    report.getCompletedAt() == null ? null : report.getCompletedAt().toString(),
                    report.getCheckId(), CheckStatus.PENDING.name(), CheckStatus.RUNNING.name());
        } catch (Exception e) {
            throw new IllegalStateException("failed to persist report " + report.getCheckId(), e);
        }
    }

    public Optional<AuditReport> findReport(String checkId) {
        List<String> rows = jdbc.query("SELECT report_json FROM checks WHERE check_id=?",
                (rs, n) -> rs.getString(1), checkId);
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(rows.get(0)));
    }

    public Optional<CheckStatus> findStatus(String checkId) {
        List<String> rows = jdbc.query("SELECT status FROM checks WHERE check_id=?",
                (rs, n) -> rs.getString(1), checkId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(CheckStatus.valueOf(rows.get(0)));
    }

    /** Returns the cached terminal report for identical contract content, if any. */
    public Optional<AuditReport> findCompletedByContractKey(String contractKey) {
        List<String> rows = jdbc.query(
                "SELECT report_json FROM checks WHERE contract_key=? AND status=?"
                        + " ORDER BY created_at DESC LIMIT 1",
                (rs, n) -> rs.getString(1), contractKey, CheckStatus.COMPLETED.name());
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(rows.get(0)));
    }

    /** Discards non-terminal records left over from a previous run (crash recovery). */
    public int discardIncomplete() {
        return jdbc.update("DELETE FROM checks WHERE status NOT IN (?,?,?,?)",
                TERMINAL.toArray());
    }

    private AuditReport deserialize(String json) {
        try {
            return objectMapper.readValue(json, AuditReport.class);
        } catch (Exception e) {
            throw new IllegalStateException("corrupt stored report", e);
        }
    }
}
