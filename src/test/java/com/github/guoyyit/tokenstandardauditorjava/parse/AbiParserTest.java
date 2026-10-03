package com.github.guoyyit.tokenstandardauditorjava.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.tokenstandardauditorjava.model.AbiEvent;
import com.github.guoyyit.tokenstandardauditorjava.model.AbiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbiParserTest {

    private AbiParser parser;

    @BeforeEach
    void setUp() {
        parser = new AbiParser(new ObjectMapper());
    }

    @Test
    void parsesFunctionsAndEvents() {
        String abi = """
                [
                  {"type":"function","name":"transfer","stateMutability":"nonpayable",
                   "inputs":[{"name":"to","type":"address"},{"name":"value","type":"uint256"}],
                   "outputs":[{"name":"","type":"bool"}]},
                  {"type":"event","name":"Transfer",
                   "inputs":[{"name":"from","type":"address","indexed":true},
                             {"name":"to","type":"address","indexed":true},
                             {"name":"value","type":"uint256","indexed":false}]}
                ]
                """;
        AbiParser.ParsedAbi parsed = parser.parse(abi);
        assertEquals(1, parsed.functions().size());
        assertEquals(1, parsed.events().size());

        AbiFunction transfer = parsed.functions().get(0);
        assertEquals("transfer(address,uint256)", transfer.signature());
        assertEquals("0xa9059cbb", transfer.selector());
        assertEquals("bool", transfer.outputs().get(0).canonicalType());

        AbiEvent event = parsed.events().get(0);
        assertEquals("Transfer(address,address,uint256)", event.signature());
        assertEquals("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef", event.topic());
        assertTrue(event.inputs().get(0).indexed());
        assertTrue(event.inputs().get(1).indexed());
    }

    @Test
    void canonicalizesShortTypes() {
        String abi = """
                [{"type":"function","name":"f","stateMutability":"view",
                  "inputs":[{"name":"a","type":"uint"},{"name":"b","type":"int"},{"name":"c","type":"bytes32"}],
                  "outputs":[]}]
                """;
        AbiFunction f = parser.parse(abi).functions().get(0);
        assertEquals("f(uint256,int256,bytes32)", f.signature());
    }

    @Test
    void rejectsMalformedJson() {
        ParseException e = assertThrows(ParseException.class, () -> parser.parse("{not json"));
        assertEquals(ParseException.ABI_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsNonArray() {
        ParseException e = assertThrows(ParseException.class, () -> parser.parse("{}"));
        assertEquals(ParseException.ABI_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsUnknownItemType() {
        String abi = """
                [{"type":"bogus","name":"x","inputs":[],"outputs":[]}]
                """;
        ParseException e = assertThrows(ParseException.class, () -> parser.parse(abi));
        assertEquals(ParseException.ABI_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsUnknownSolidityType() {
        String abi = """
                [{"type":"function","name":"f","stateMutability":"view",
                  "inputs":[{"name":"a","type":"widget"}],
                  "outputs":[]}]
                """;
        ParseException e = assertThrows(ParseException.class, () -> parser.parse(abi));
        assertEquals(ParseException.ABI_PARSE_ERROR, e.getCode());
    }

    @Test
    void ignoresConstructorFallbackReceiveAndError() {
        String abi = """
                [
                  {"type":"constructor","inputs":[]},
                  {"type":"fallback","stateMutability":"payable","inputs":[],"outputs":[]},
                  {"type":"receive","stateMutability":"payable","inputs":[],"outputs":[]},
                  {"type":"error","name":"Oops","inputs":[]}
                ]
                """;
        AbiParser.ParsedAbi parsed = parser.parse(abi);
        assertEquals(0, parsed.functions().size());
        assertEquals(0, parsed.events().size());
    }
}
