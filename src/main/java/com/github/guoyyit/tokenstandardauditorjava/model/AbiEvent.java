package com.github.guoyyit.tokenstandardauditorjava.model;

import java.util.List;

/**
 * An event declared in the contract ABI.
 *
 * @param name      event name
 * @param inputs    event parameters (indexed flag matters)
 * @param signature canonical signature, e.g. "Transfer(address,address,uint256)"
 * @param topic     32-byte topic0 hash as 0x-prefixed hex
 */
public record AbiEvent(
        String name,
        List<AbiParam> inputs,
        String signature,
        String topic
) {
    public long indexedCount() {
        return inputs.stream().filter(AbiParam::indexed).count();
    }
}
