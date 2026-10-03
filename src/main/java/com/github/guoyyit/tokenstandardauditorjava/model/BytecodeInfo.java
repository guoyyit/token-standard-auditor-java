package com.github.guoyyit.tokenstandardauditorjava.model;

import java.util.Set;

/**
 * Parsed EVM bytecode.
 *
 * @param raw              original raw bytecode hex (0x-prefixed, lower-cased)
 * @param code             bytecode with the Solidity metadata suffix stripped
 * @param selectors         4-byte function selectors found in the bytecode (0x-prefixed hex)
 * @param topics            32-byte event topic hashes found in the bytecode (0x-prefixed hex)
 * @param size              size of the stripped code in bytes
 * @param metadataPresent   whether a Solidity metadata suffix was detected
 */
public record BytecodeInfo(
        String raw,
        String code,
        Set<String> selectors,
        Set<String> topics,
        int size,
        boolean metadataPresent
) {
    public boolean implementsFunction(String selector) {
        return selectors.contains(selector.toLowerCase());
    }

    public boolean emitsEvent(String topic) {
        return topics.contains(topic.toLowerCase());
    }
}
