package com.github.guoyyit.tokenstandardauditorjava.model;

import java.util.List;

/**
 * A function declared in the contract ABI.
 *
 * @param name          function name
 * @param inputs        input parameters
 * @param outputs       output parameters
 * @param stateMutability mutability: view, pure, payable, nonpayable
 * @param signature     canonical signature, e.g. "transfer(address,uint256)"
 * @param selector      4-byte selector as 0x-prefixed hex
 */
public record AbiFunction(
        String name,
        List<AbiParam> inputs,
        List<AbiParam> outputs,
        String stateMutability,
        String signature,
        String selector
) {
    public boolean isView() {
        return "view".equals(stateMutability) || "pure".equals(stateMutability);
    }

    public boolean isPayable() {
        return "payable".equals(stateMutability);
    }
}
