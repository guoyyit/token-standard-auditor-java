package com.github.guoyyit.tokenstandardauditorjava.web;

import com.github.guoyyit.tokenstandardauditorjava.api.AuditException;
import com.github.guoyyit.tokenstandardauditorjava.api.ContractSubmission;
import com.github.guoyyit.tokenstandardauditorjava.api.ErrorCode;
import com.github.guoyyit.tokenstandardauditorjava.domain.AuditReport;
import com.github.guoyyit.tokenstandardauditorjava.domain.CheckStatus;
import com.github.guoyyit.tokenstandardauditorjava.domain.TokenStandard;
import com.github.guoyyit.tokenstandardauditorjava.engine.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for submitting and querying audit checks.
 */
@RestController
@RequestMapping("/api/audits")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    public record SubmissionRequest(String contractId, String abi, String bytecode,
                                    String standard, String standardVersion) {
    }

    public record BatchRequest(List<SubmissionRequest> submissions) {
    }

    @PostMapping
    public Map<String, String> submit(@RequestBody SubmissionRequest request) {
        String checkId = auditService.submit(toSubmission(request));
        return Map.of("checkId", checkId);
    }

    @PostMapping("/batch")
    public Map<String, List<String>> submitBatch(@RequestBody BatchRequest request) {
        List<ContractSubmission> submissions = request.submissions().stream()
                .map(AuditController::toSubmission).toList();
        return Map.of("checkIds", auditService.submitBatch(submissions));
    }

    @GetMapping("/{checkId}")
    public ResponseEntity<?> getReport(@PathVariable String checkId) {
        Optional<AuditReport> report = auditService.getReport(checkId);
        if (report.isPresent()) {
            return ResponseEntity.ok(report.get());
        }
        Optional<CheckStatus> status = auditService.getStatus(checkId);
        if (status.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "unknown checkId", "checkId", checkId));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("checkId", checkId, "status", status.get().name()));
    }

    @GetMapping("/{checkId}/status")
    public ResponseEntity<?> getStatus(@PathVariable String checkId) {
        Optional<CheckStatus> status = auditService.getStatus(checkId);
        if (status.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "unknown checkId", "checkId", checkId));
        }
        return ResponseEntity.ok(Map.of("checkId", checkId, "status", status.get().name()));
    }

    @PostMapping("/{checkId}/cancel")
    public Map<String, Object> cancel(@PathVariable String checkId) {
        boolean cancelled = auditService.cancel(checkId);
        return Map.of("checkId", checkId, "cancelled", cancelled);
    }

    private static ContractSubmission toSubmission(SubmissionRequest request) {
        TokenStandard standard = parseStandard(request.standard());
        return new ContractSubmission(request.contractId(), request.abi(), request.bytecode(),
                standard, request.standardVersion());
    }

    static TokenStandard parseStandard(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AuditException(ErrorCode.UNKNOWN_STANDARD_VERSION, "standard is required");
        }
        try {
            return TokenStandard.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AuditException(ErrorCode.UNKNOWN_STANDARD_VERSION,
                    "unknown standard: " + raw);
        }
    }
}
