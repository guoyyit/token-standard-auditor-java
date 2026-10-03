package com.github.guoyyit.tokenstandardauditorjava.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Keccak256Test {

    @Test
    void knownSelectors() {
        assertEquals("0xa9059cbb", Keccak256.selector("transfer(address,uint256)"));
        assertEquals("0x095ea7b3", Keccak256.selector("approve(address,uint256)"));
        assertEquals("0x70a08231", Keccak256.selector("balanceOf(address)"));
        assertEquals("0x18160ddd", Keccak256.selector("totalSupply()"));
        assertEquals("0x23b872dd", Keccak256.selector("transferFrom(address,address,uint256)"));
        assertEquals("0xdd62ed3e", Keccak256.selector("allowance(address,address)"));
    }

    @Test
    void knownTopics() {
        assertEquals("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                Keccak256.topic("Transfer(address,address,uint256)"));
        assertEquals("0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925",
                Keccak256.topic("Approval(address,address,uint256)"));
    }
}
