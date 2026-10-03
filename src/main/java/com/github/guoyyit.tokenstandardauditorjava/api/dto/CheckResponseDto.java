package com.github.guoyyit.tokenstandardauditorjava.api.dto;

import com.github.guoyyit.tokenstandardauditorjava.model.CheckRecord;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckReport;
import com.github.guoyyit.tokenstandardauditorjava.model.CheckStatus;

import java.time.Instant;

/**
 * Response representation of a check.
 */
public record CheckResponseDto(
        String id,
        CheckStatus status,
        String standard,
        String version,
        String abiHash,
        String bytecodeHash,
        CheckReport report,
        String errorCode,
        String errorMessage,
        Instant submittedAt,
        Instant completedAt,
        boolean cached,
        String cachedFrom
) {
    public static CheckResponseDto from(CheckRecord record) {
        return new CheckResponseDto(
                record.getId(),
                record.getStatus(),
                record.getStandard(),
                record.getVersion(),
                record.getAbiHash(),
                record.getBytecodeHash(),
                record.getReport(),
                record.getErrorCode(),
                record.getErrorMessage(),
                record.getSubmittedAt(),
                record.getCompletedAt(),
                record.isCached(),
                record.getCachedFrom());
    }
}
