package com.github.guoyyit.tokenstandardauditorjava.api.dto;

/**
 * Input for a compliance check.
 *
 * @param abi            contract ABI as a JSON array string
 * @param bytecode       contract bytecode as a hex string
 * @param standard       target standard, e.g. ERC20
 * @param version        target rule-set version; defaults to the standard's default version
 * @param timeoutSeconds per-check timeout in seconds; defaults to the service default
 */
public record CheckRequestDto(
        String abi,
        String bytecode,
        String standard,
        String version,
        Long timeoutSeconds
) {
}
