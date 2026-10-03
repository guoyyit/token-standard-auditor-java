package com.github.guoyyit.tokenstandardauditorjava.model;

/**
 * Input for a compliance check.
 *
 * @param abi            contract ABI as a JSON array string
 * @param bytecode       contract bytecode as a hex string
 * @param standard       target standard, e.g. "ERC20"
 * @param version        target rule-set version; null means the default version
 * @param timeoutSeconds per-check timeout in seconds; null means the service default
 */
public record CheckRequest(
        String abi,
        String bytecode,
        String standard,
        String version,
        Long timeoutSeconds
) {
}
