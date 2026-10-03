package com.github.guoyyit.tokenstandardauditorjava.model;

import java.time.Instant;
import java.util.List;

/**
 * The compliance report produced by a successful check.
 *
 * @param checkId       id of the check that produced the report
 * @param standard      target standard, e.g. "ERC20"
 * @param version       target rule-set version, e.g. "1.0"
 * @param abiHash       SHA-256 of the submitted ABI
 * @param bytecodeHash  SHA-256 of the submitted bytecode
 * @param findings      all findings, ordered by rule id
 * @param errorCount    number of ERROR findings
 * @param warningCount  number of WARNING findings
 * @param infoCount     number of INFO findings
 * @param compliant     true if no ERROR findings were produced
 * @param createdAt     when the report was created
 */
public record CheckReport(
        String checkId,
        String standard,
        String version,
        String abiHash,
        String bytecodeHash,
        List<Finding> findings,
        int errorCount,
        int warningCount,
        int infoCount,
        boolean compliant,
        Instant createdAt
) {
}
