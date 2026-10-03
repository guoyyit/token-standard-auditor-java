package com.github.guoyyit.tokenstandardauditorjava.tests;

import com.github.guoyyit.tokenstandardauditorjava.api.AuditException;
import com.github.guoyyit.tokenstandardauditorjava.api.ErrorCode;
import com.github.guoyyit.tokenstandardauditorjava.bytecode.EvmBytecodeParser;
import com.github.guoyyit.tokenstandardauditorjava.bytecode.ParsedBytecode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvmBytecodeParserTest {

    private final EvmBytecodeParser parser = new EvmBytecodeParser();

    @Test
    void extractsSelectorsAndTopicsWithOffsets() {
        String selector = TestFixtures.selector("transfer(address,uint256)");
        String topic = TestFixtures.topic("Transfer(address,address,uint256)");
        ParsedBytecode parsed = parser.parse("0x63" + selector + "7f" + topic + "00");
        assertTrue(parsed.firstSelectorOffset(selector).isPresent());
        assertEquals(0, parsed.firstSelectorOffset(selector).get());
        assertTrue(parsed.firstTopicOffset(topic).isPresent());
        assertEquals(5, parsed.firstTopicOffset(topic).get());
    }

    @Test
    void pushDataIsNotMisreadAsOpcodes() {
        // PUSH5 data that itself contains a PUSH4 opcode byte 0x63 followed by fake selector
        String fakeSelector = "deadbeef";
        ParsedBytecode parsed = parser.parse("0x6463" + fakeSelector + "aabbcc00");
        assertTrue(parsed.firstSelectorOffset(fakeSelector).isEmpty());
    }

    @Test
    void rejectsOddLengthHex() {
        AuditException e = assertThrows(AuditException.class, () -> parser.parse("0x123"));
        assertEquals(ErrorCode.BYTECODE_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsInvalidHexCharacters() {
        AuditException e = assertThrows(AuditException.class, () -> parser.parse("0xzz"));
        assertEquals(ErrorCode.BYTECODE_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsMissingBytecode() {
        AuditException e = assertThrows(AuditException.class, () -> parser.parse("  "));
        assertEquals(ErrorCode.BYTECODE_PARSE_ERROR, e.getCode());
    }
}
