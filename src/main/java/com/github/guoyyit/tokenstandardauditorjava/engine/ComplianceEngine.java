package com.github.guoyyit.tokenstandardauditorjava.engine;

import com.github.guoyyit.tokenstandardauditorjava.abi.AbiModels.AbiEvent;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiModels.AbiFunction;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiModels.AbiParam;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiModels.ContractAbi;
import com.github.guoyyit.tokenstandardauditorjava.bytecode.ParsedBytecode;
import com.github.guoyyit.tokenstandardauditorjava.domain.Finding;
import com.github.guoyyit.tokenstandardauditorjava.domain.Location;
import com.github.guoyyit.tokenstandardauditorjava.domain.Severity;
import com.github.guoyyit.tokenstandardauditorjava.rules.RuleDefinition;
import com.github.guoyyit.tokenstandardauditorjava.util.Keccak256;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, deterministic evaluator: applies one rule set snapshot to a parsed
 * ABI + bytecode pair and produces findings.
 */
@Component
public class ComplianceEngine {

    public List<Finding> evaluate(RuleDefinition rules, ContractAbi abi, ParsedBytecode bytecode) {
        List<Finding> findings = new ArrayList<>();
        Map<String, AbiFunction> abiFunctions = new HashMap<>();
        for (AbiFunction function : abi.functions()) {
            abiFunctions.put(function.canonicalSignature(), function);
        }
        Map<String, AbiEvent> abiEvents = new HashMap<>();
        for (AbiEvent event : abi.events()) {
            abiEvents.put(event.canonicalSignature(), event);
        }

        for (RuleDefinition.FunctionRule rule : rules.functions()) {
            checkFunction(rule, abiFunctions, bytecode, findings);
        }
        for (RuleDefinition.EventRule rule : rules.events()) {
            checkEvent(rule, abiEvents, bytecode, findings);
        }
        return List.copyOf(findings);
    }

    private void checkFunction(RuleDefinition.FunctionRule rule,
                               Map<String, AbiFunction> abiFunctions,
                               ParsedBytecode bytecode,
                               List<Finding> findings) {
        String signature = rule.name() + "(" + String.join(",", rule.inputs()) + ")";
        Location location = Location.abiFunction(rule.name(), signature);
        AbiFunction declared = abiFunctions.get(signature);
        if (declared == null) {
            if (rule.required()) {
                findings.add(new Finding(ruleId(rule, signature), rule.severity(),
                        "required function " + signature + " is not declared in the ABI", location));
            } else {
                findings.add(new Finding(ruleId(rule, signature), rule.severity(),
                        "optional function " + signature + " is not declared in the ABI", location));
            }
            return;
        }

        List<String> declaredOutputs = declared.outputs().stream().map(AbiParam::type).toList();
        if (!declaredOutputs.equals(rule.outputs())) {
            findings.add(new Finding(ruleId(rule, signature), Severity.WARNING,
                    "function " + signature + " return types " + declaredOutputs
                            + " do not match the standard " + rule.outputs(), location));
        }
        if (rule.stateMutability() != null && declared.stateMutability() != null
                && !rule.stateMutability().equals(declared.stateMutability())) {
            findings.add(new Finding(ruleId(rule, signature), Severity.WARNING,
                    "function " + signature + " stateMutability \"" + declared.stateMutability()
                            + "\" does not match the standard \"" + rule.stateMutability() + "\"", location));
        }

        String selector = Keccak256.functionSelector(signature);
        if (bytecode.firstSelectorOffset(selector).isEmpty()) {
            findings.add(new Finding(ruleId(rule, signature), Severity.ERROR,
                    "function " + signature + " is declared in the ABI but its selector 0x" + selector
                            + " is not present in the bytecode", location));
        }
    }

    private void checkEvent(RuleDefinition.EventRule rule,
                            Map<String, AbiEvent> abiEvents,
                            ParsedBytecode bytecode,
                            List<Finding> findings) {
        List<String> types = rule.params().stream().map(RuleDefinition.EventParam::type).toList();
        String signature = rule.name() + "(" + String.join(",", types) + ")";
        Location location = Location.abiEvent(rule.name(), signature);
        AbiEvent declared = abiEvents.get(signature);
        if (declared == null) {
            findings.add(new Finding(ruleId(rule, signature), rule.severity(),
                    (rule.required() ? "required" : "optional") + " event " + signature
                            + " is not declared in the ABI", location));
            return;
        }

        for (int i = 0; i < rule.params().size() && i < declared.inputs().size(); i++) {
            RuleDefinition.EventParam expected = rule.params().get(i);
            AbiParam actual = declared.inputs().get(i);
            if (expected.indexed() != actual.indexed()) {
                findings.add(new Finding(ruleId(rule, signature), rule.severity(),
                        "event " + signature + " param #" + i + " (" + expected.name() + ") indexed="
                                + actual.indexed() + " but the standard requires indexed=" + expected.indexed(),
                        location));
            }
        }

        String topic = Keccak256.hexDigest(signature);
        if (bytecode.firstTopicOffset(topic).isEmpty()) {
            findings.add(new Finding(ruleId(rule, signature), Severity.ERROR,
                    "event " + signature + " is declared in the ABI but its topic 0x" + topic
                            + " is not present in the bytecode", location));
        }
    }

    private String ruleId(RuleDefinition.FunctionRule rule, String signature) {
        return "function:" + signature;
    }

    private String ruleId(RuleDefinition.EventRule rule, String signature) {
        return "event:" + signature;
    }
}
