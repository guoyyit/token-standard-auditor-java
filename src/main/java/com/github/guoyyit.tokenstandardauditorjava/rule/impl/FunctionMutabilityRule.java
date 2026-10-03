package com.github.guoyyit.tokenstandardauditorjava.rule.impl;

import com.github.guoyyit.tokenstandardauditorjava.model.AbiFunction;
import com.github.guoyyit.tokenstandardauditorjava.model.ContractContract;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import com.github.guoyyit.tokenstandardauditorjava.rule.Rule;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a function's state mutability matches the expected value.
 */
public class FunctionMutabilityRule implements Rule {

    private final String id;
    private final Severity severity;
    private final String description;
    private final String standard;
    private final String version;
    private final String functionSignature;
    private final String expectedMutability;

    public FunctionMutabilityRule(String id, Severity severity, String description,
                                  String standard, String version, String functionSignature,
                                  String expectedMutability) {
        this.id = id;
        this.severity = severity;
        this.description = description;
        this.standard = standard;
        this.version = version;
        this.functionSignature = functionSignature;
        this.expectedMutability = expectedMutability;
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
        AbiFunction function = contract.functionBySignature(functionSignature).orElse(null);
        if (function == null) {
            return findings;
        }
        if (!expectedMutability.equals(function.stateMutability())) {
            findings.add(new Finding(id, severity, standard, version,
                    "Function " + functionSignature + " is " + function.stateMutability()
                            + " but expected " + expectedMutability,
                    Finding.Location.function(function.name(), function.signature(), function.selector())));
        }
        return findings;
    }
}
