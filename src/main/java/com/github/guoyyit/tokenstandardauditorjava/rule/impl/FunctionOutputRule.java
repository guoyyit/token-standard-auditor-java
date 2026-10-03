package com.github.guoyyit.tokenstandardauditorjava.rule.impl;

import com.github.guoyyit.tokenstandardauditorjava.model.AbiFunction;
import com.github.guoyyit.tokenstandardauditorjava.model.AbiParam;
import com.github.guoyyit.tokenstandardauditorjava.model.ContractContract;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import com.github.guoyyit.tokenstandardauditorjava.rule.Rule;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a function's return types match the expected canonical types.
 */
public class FunctionOutputRule implements Rule {

    private final String id;
    private final Severity severity;
    private final String description;
    private final String standard;
    private final String version;
    private final String functionSignature;
    private final List<String> expectedOutputTypes;

    public FunctionOutputRule(String id, Severity severity, String description,
                              String standard, String version, String functionSignature,
                              List<String> expectedOutputTypes) {
        this.id = id;
        this.severity = severity;
        this.description = description;
        this.standard = standard;
        this.version = version;
        this.functionSignature = functionSignature;
        this.expectedOutputTypes = List.copyOf(expectedOutputTypes);
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
        List<String> actual = function.outputs().stream().map(AbiParam::canonicalType).toList();
        if (!actual.equals(expectedOutputTypes)) {
            findings.add(new Finding(id, severity, standard, version,
                    "Function " + functionSignature + " returns " + actual
                            + " but expected " + expectedOutputTypes,
                    Finding.Location.function(function.name(), function.signature(), function.selector())));
        }
        return findings;
    }
}
