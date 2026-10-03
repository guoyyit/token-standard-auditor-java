package com.github.guoyyit.tokenstandardauditorjava.rule;

import com.github.guoyyit.tokenstandardauditorjava.model.ContractContract;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.model.Severity;

import java.util.List;

/**
 * A single compliance rule.
 *
 * <p>Rules are stateless and operate on a parsed {@link ContractContract}. Each rule declares its
 * own {@link #id()}, {@link #severity()} and {@link #description()}.
 */
public interface Rule {

    /**
     * Unique rule identifier, e.g. "ERC20-REQ-001".
     */
    String id();

    /**
     * Severity of findings produced by this rule.
     */
    Severity severity();

    /**
     * Human-readable description of what the rule checks.
     */
    String description();

    /**
     * Evaluates the rule against the given contract.
     *
     * @param contract parsed contract (ABI + bytecode)
     * @return findings; empty list if the rule passes
     */
    List<Finding> evaluate(ContractContract contract);
}
