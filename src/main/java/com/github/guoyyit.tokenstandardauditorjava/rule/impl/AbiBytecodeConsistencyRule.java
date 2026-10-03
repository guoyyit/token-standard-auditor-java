package com.github.guoyyit.tokenstandardauditorjava.rule.impl;

import com.github.guoyyit.tokenstandardauditorjava.model.AbiFunction;
import com.github.guoyyit.tokenstandardauditorjava.model.ContractContract;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import com.github.guoyyit.tokenstandardauditorjava.rule.Rule;

import java.util.ArrayList;
import java.util.List;

/**
 * Reports functions that are declared in the ABI but not implemented in the bytecode.
 *
 * <p>This is the interface-vs-implementation consistency check: every function the interface
 * promises must be present in the deployed bytecode.
 */
public class AbiBytecodeConsistencyRule implements Rule {

    private final String id;
    private final Severity severity;
    private final String description;
    private final String standard;
    private final String version;

    public AbiBytecodeConsistencyRule(String id, Severity severity, String description,
                                      String standard, String version) {
        this.id = id;
        this.severity = severity;
        this.description = description;
        this.standard = standard;
        this.version = version;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<Finding> evaluate(ContractContract contract) {
        List<Finding> findings = new ArrayList<>();
        for (AbiFunction function : contract.functions()) {
            if (!contract.bytecode().implementsFunction(function.selector())) {
                findings.add(new Finding(id, severity, standard, version,
                        "Function " + function.signature()
                                + " is declared in the ABI but not implemented in the bytecode",
                        Finding.Location.function(function.name(), function.signature(), function.selector())));
            }
        }
        return findings;
    }
}
