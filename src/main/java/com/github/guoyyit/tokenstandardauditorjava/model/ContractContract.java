package com.github.guoyyit.tokenstandardauditorjava.model;

import java.util.List;
import java.util.Optional;

/**
 * A fully parsed contract: its interface (ABI) and its implementation (bytecode).
 *
 * @param functions    functions declared in the ABI (constructors excluded)
 * @param events       events declared in the ABI
 * @param bytecode     parsed bytecode
 */
public record ContractContract(
        List<AbiFunction> functions,
        List<AbiEvent> events,
        BytecodeInfo bytecode
) {
    public Optional<AbiFunction> functionByName(String name) {
        return functions.stream().filter(f -> f.name().equals(name)).findFirst();
    }

    public Optional<AbiFunction> functionBySignature(String signature) {
        return functions.stream().filter(f -> f.signature().equals(signature)).findFirst();
    }

    public Optional<AbiEvent> eventByName(String name) {
        return events.stream().filter(e -> e.name().equals(name)).findFirst();
    }
}
