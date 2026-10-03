package com.github.guoyyit.tokenstandardauditorjava.rule.impl;

import com.github.guoyyit.tokenstandardauditorjava.model.AbiEvent;
import com.github.guoyyit.tokenstandardauditorjava.model.ContractContract;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import com.github.guoyyit.tokenstandardauditorjava.rule.Rule;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that an existing event's indexed parameters match the expected positions.
 */
public class EventIndexingRule implements Rule {

    private final String id;
    private final Severity severity;
    private final String description;
    private final String standard;
    private final String version;
    private final String eventSignature;
    private final List<Boolean> expectedIndexed;

    public EventIndexingRule(String id, Severity severity, String description,
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
        AbiEvent event = contract.events().stream()
                .filter(e -> e.signature().equals(eventSignature))
                .findFirst()
                .orElse(null);
        if (event == null) {
            return findings;
        }
        List<Boolean> actual = event.inputs().stream().map(p -> p.indexed()).toList();
        if (!actual.equals(expectedIndexed)) {
            findings.add(new Finding(id, severity, standard, version,
                    "Event " + eventSignature + " indexed parameters are " + actual
                            + " but expected " + expectedIndexed,
                    Finding.Location.event(event.name(), event.signature(), event.topic())));
        }
        return findings;
    }
}
