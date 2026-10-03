package com.github.guoyyit.tokenstandardauditorjava.bytecode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Structured view of decoded EVM bytecode: function selectors, event topics
 * and the raw opcode stream with offsets.
 */
public final class ParsedBytecode {

    private final byte[] rawCode;
    private final List<EvmOpcode> opcodes;
    private final Map<String, List<Integer>> functionSelectors;
    private final Map<String, List<Integer>> eventTopics;

    public ParsedBytecode(byte[] rawCode,
                          List<EvmOpcode> opcodes,
                          Map<String, List<Integer>> functionSelectors,
                          Map<String, List<Integer>> eventTopics) {
        this.rawCode = rawCode;
        this.opcodes = opcodes;
        this.functionSelectors = functionSelectors;
        this.eventTopics = eventTopics;
    }

    public byte[] getRawCode() {
        return rawCode;
    }

    public List<EvmOpcode> getOpcodes() {
        return opcodes;
    }

    /** 4-byte selector (8 hex chars, lower case) -> offsets where pushed. */
    public Map<String, List<Integer>> getFunctionSelectors() {
        return functionSelectors;
    }

    /** 32-byte topic (64 hex chars, lower case) -> offsets where pushed. */
    public Map<String, List<Integer>> getEventTopics() {
        return eventTopics;
    }

    public Optional<Integer> firstSelectorOffset(String selectorHex) {
        List<Integer> offsets = functionSelectors.get(selectorHex);
        return offsets == null || offsets.isEmpty() ? Optional.empty() : Optional.of(offsets.get(0));
    }

    public Optional<Integer> firstTopicOffset(String topicHex) {
        List<Integer> offsets = eventTopics.get(topicHex);
        return offsets == null || offsets.isEmpty() ? Optional.empty() : Optional.of(offsets.get(0));
    }
}
