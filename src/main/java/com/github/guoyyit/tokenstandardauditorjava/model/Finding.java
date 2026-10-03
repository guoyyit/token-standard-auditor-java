package com.github.guoyyit.tokenstandardauditorjava.model;

/**
 * A single compliance finding produced by a rule evaluation.
 *
 * @param ruleId   identifier of the rule that produced the finding
 * @param severity  severity level
 * @param standard  target standard, e.g. "ERC20"
 * @param version   target rule-set version, e.g. "1.0"
 * @param message   human-readable description of the issue
 * @param location  location of the issue within the contract (function/event), may be null
 */
public record Finding(
        String ruleId,
        Severity severity,
        String standard,
        String version,
        String message,
        Location location
) {
    /**
     * Location of a finding within the contract interface.
     *
     * @param kind      FUNCTION or EVENT
     * @param name      interface name, e.g. "transfer"
     * @param signature canonical signature, e.g. "transfer(address,uint256)"
     * @param selector  4-byte function selector (hex, 0x-prefixed), may be null for events
     * @param topic     32-byte event topic (hex, 0x-prefixed), may be null for functions
     */
    public record Location(
            LocationKind kind,
            String name,
            String signature,
            String selector,
            String topic
    ) {
        public static Location function(String name, String signature, String selector) {
            return new Location(LocationKind.FUNCTION, name, signature, selector, null);
        }

        public static Location event(String name, String signature, String topic) {
            return new Location(LocationKind.EVENT, name, signature, null, topic);
        }
    }

    public enum LocationKind {
        FUNCTION,
        EVENT
    }
}
