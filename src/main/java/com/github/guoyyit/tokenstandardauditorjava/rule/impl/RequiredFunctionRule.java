package com.github.guoyyit.tokenstandardauditorjava.rule.impl;

import com.github.guoyyit.tokenstandardauditorjava.model.AbiFunction;
import com.github.guoyyit.tokenstandardauditorjava.model.ContractContract;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import com.github.guoyyit.tokenstandardauditorjava.rule.Rule;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a required function is declared in the ABI and implemented in the bytecode.
 */
public class RequiredFunctionRule implements Rule {

    private final String id;
    private final Severity severity;
    private final String description;
    private final String standard;
    private final String version;
    private final String functionSignature;

    public RequiredFunctionRule(String id, Severity severity, String description,
                                String standard, String version, String functionSignature) {
        this.id = id;
        this.severity = severity;
        this.description = description;
        this.standard = standard;
        this.version = version;
        this.functionSignature = functionSignature;
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
            findings.add(new Finding(id, severity, standard, version,
                    "Required function " + functionSignature + " is not declared in the ABI",
                    Finding.Location.function(signatureName(), functionSignature, null)));
            return findings;
        }
        if (!contract.bytecode().implementsFunction(function.selector())) {
            findings.add(new Finding(id, severity, standard, version,
                    "Function " + functionSignature + " is declared in the ABI but not implemented in the bytecode",
                    Finding.Location.function(function.name(), function.signature(), function.selector())));
        }
        return findings;
    }

    private String signatureName() {
        int paren = functionSignature.indexOf('(');
        return paren < 0 ? functionSignature : functionSignature.substring(0, paren);
    }
}
