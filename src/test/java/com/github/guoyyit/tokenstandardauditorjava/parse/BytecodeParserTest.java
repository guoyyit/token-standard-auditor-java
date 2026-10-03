package com.github.guoyyit.tokenstandardauditorjava.parse;

import com.github.guoyyit.tokenstandardauditorjava.model.BytecodeInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BytecodeParserTest {

    private BytecodeParser parser;

    @BeforeEach
    void setUp() {
        parser = new BytecodeParser();
    }

    @Test
    void extractsSelectorsAndTopics() {
        // PUSH4 transfer(address,uint256) selector, PUSH32 Transfer topic
        String bytecode = "0x63a9059cbb63095ea7b37f"
                + "ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
        BytecodeInfo info = parser.parse(bytecode);
        assertTrue(info.implementsFunction("0xa9059cbb"));
        assertTrue(info.implementsFunction("0x095ea7b3"));
        assertTrue(info.emitsEvent("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"));
        assertFalse(info.implementsFunction("0xdeadbeef"));
    }

    @Test
    void acceptsHexWithout0xPrefix() {
        String bytecode = "63a9059cbb";
        BytecodeInfo info = parser.parse(bytecode);
        assertTrue(info.implementsFunction("0xa9059cbb"));
    }

    @Test
    void stripsMetadataSuffix() {
        // code + CBOR metadata (0xa2...) + 2-byte length (0x00 0x03)
        String code = "63a9059cbb";
        String metadata = "a264736f6c63434d4e56313133";
        String length = "000d";
        BytecodeInfo info = parser.parse("0x" + code + metadata + length);
        assertTrue(info.metadataPresent());
        assertEquals(code.length() / 2, info.size());
        assertTrue(info.implementsFunction("0xa9059cbb"));
    }

    @Test
    void rejectsEmpty() {
        ParseException e = assertThrows(ParseException.class, () -> parser.parse(""));
        assertEquals(ParseException.BYTECODE_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsOddLength() {
        ParseException e = assertThrows(ParseException.class, () -> parser.parse("0xabc"));
        assertEquals(ParseException.BYTECODE_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsNonHex() {
        ParseException e = assertThrows(ParseException.class, () -> parser.parse("0xzzzz"));
        assertEquals(ParseException.BYTECODE_PARSE_ERROR, e.getCode());
    }

    @Test
    void rawSearchFindsSelector() {
        String bytecode = "0x600163a9059cbb6002";
        BytecodeInfo info = parser.parse(bytecode);
        assertTrue(parser.containsSelector(info, "0xa9059cbb"));
    }
}
