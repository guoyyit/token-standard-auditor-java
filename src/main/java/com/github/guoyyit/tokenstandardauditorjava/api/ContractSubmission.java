package com.github.guoyyit.tokenstandardauditorjava.api;

import com.github.guoyyit.tokenstandardauditorjava.domain.TokenStandard;

/**
 * A single contract audit request: raw ABI JSON plus hex-encoded bytecode.
 */
public record ContractSubmission(
        String contractId,
        String abiJson,
        String bytecodeHex,
        TokenStandard standard,
        String standardVersion) {
}
