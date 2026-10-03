package com.github.guoyyit.tokenstandardauditorjava.model;

/**
 * A single ABI parameter (input or output).
 *
 * @param name          parameter name (may be empty)
 * @param type          original Solidity type as declared, e.g. "uint"
 * @param canonicalType canonical type used for signature computation, e.g. "uint256"
 * @param indexed       whether the parameter is indexed (events only)
 */
public record AbiParam(String name, String type, String canonicalType, boolean indexed) {
}
