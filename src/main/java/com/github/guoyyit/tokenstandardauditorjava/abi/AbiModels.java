package com.github.guoyyit.tokenstandardauditorjava.abi;

import java.util.List;

/**
 * Parsed contract ABI model types.
 */
public final class AbiModels {

    private AbiModels() {
    }

    public record AbiParam(String name, String type, boolean indexed, List<AbiParam> components) {
    }

    public record AbiFunction(String name,
                              List<AbiParam> inputs,
                              List<AbiParam> outputs,
                              String stateMutability) {

        public String canonicalSignature() {
            return name + "(" + String.join(",", inputs.stream().map(AbiParam::type).toList()) + ")";
        }
    }

    public record AbiEvent(String name, List<AbiParam> inputs) {

        public String canonicalSignature() {
            return name + "(" + String.join(",", inputs.stream().map(AbiParam::type).toList()) + ")";
        }
    }

    public record ContractAbi(List<AbiFunction> functions, List<AbiEvent> events) {
    }
}
