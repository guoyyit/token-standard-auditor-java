package com.github.guoyyit.tokenstandardauditorjava.rule.impl;

import com.github.guoyyit.tokenstandardauditorjava.model.AbiEvent;
import com.github.guoyyit.tokenstandardauditorjava.model.ContractContract;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import com.github.guoyyit.tokenstandardauditorjava.rule.Rule;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a required event is declared in the ABI and emitted in the bytecode, and that its
 * indexed parameters match the expected positions.
 */
public class RequiredEventRule implements Rule {

    private final String id;
    private final Severity severity;
    private final String description;
    private final String standard;
    private final String version;
    private final String eventSignature;
    private final List<Boolean> expectedIndexed;

    public RequiredEventRule(String id, Severity severity, String description,
                             String standard, String version, String eventSignature,
                             List<Boolean> expectedIndexed) {
        this.id = id;
        this.severity = severity;
        this.description = description;
        this.standard = standard;
        this.version = version;
        this.eventSignature = eventSignature;
        this.expectedIndexed = List.copyOf(expectedIndexed);
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
        String eventName = signatureName();
        AbiEvent event = contract.events().stream()
                .filter(e -> e.signature().equals(eventSignature))
                .findFirst()
                .orElse(null);
        if (event == null) {
            findings.add(new Finding(id, severity, standard, version,
                    "Required event " + eventSignature + " is not declared in the ABI",
                    Finding.Location.event(eventName, eventSignature, null)));
            return findings;
        }
        if (!contract.bytecode().emitsEvent(event.topic())) {
            findings.add(new Finding(id, severity, standard, version,
                    "Event " + eventSignature + " is declared in the ABI but not emitted in the bytecode",
                    Finding.Location.event(event.name(), event.signature(), event.topic())));
        }
        List<Boolean> actualIndexed = event.inputs().stream().map(p -> p.indexed()).toList();
        if (!actualIndexed.equals(expectedIndexed)) {
            findings.add(new Finding(id, severity, standard, version,
                    "Event " + eventSignature + " indexed parameters are " + actualIndexed
                            + " but expected " + expectedIndexed,
                    Finding.Location.event(event.name(), event.signature(), event.topic())));
        }
        return findings;
    }

    private String signatureName() {
        int paren = eventSignature.indexOf('(');
        return paren < 0 ? eventSignature : eventSignature.substring(0, paren);
    }
}
