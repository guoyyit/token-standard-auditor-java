package com.github.guoyyit.tokenstandardauditorjava.tests;

import com.github.guoyyit.tokenstandardauditorjava.abi.AbiModels.ContractAbi;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiParser;
import com.github.guoyyit.tokenstandardauditorjava.api.AuditException;
import com.github.guoyyit.tokenstandardauditorjava.api.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbiParserTest {

    private final AbiParser parser = new AbiParser();

    @Test
    void parsesStandardErc20Abi() {
        ContractAbi abi = parser.parse(TestFixtures.ERC20_ABI);
        assertEquals(9, abi.functions().size());
        assertEquals(2, abi.events().size());
        assertEquals("transfer(address,uint256)",
                abi.functions().get(5).canonicalSignature());
        assertTrue(abi.events().get(0).inputs().get(0).indexed());
    }

    @Test
    void rejectsMalformedJson() {
        AuditException e = assertThrows(AuditException.class, () -> parser.parse("{not json"));
        assertEquals(ErrorCode.ABI_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsMissingAbi() {
        AuditException e = assertThrows(AuditException.class, () -> parser.parse(null));
        assertEquals(ErrorCode.ABI_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsNonArrayRoot() {
        AuditException e = assertThrows(AuditException.class, () -> parser.parse("{\"type\":\"function\"}"));
        assertEquals(ErrorCode.ABI_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsUnknownElementType() {
        String abi = "[{\"type\":\"constructor2\",\"inputs\":[]}]";
        AuditException e = assertThrows(AuditException.class, () -> parser.parse(abi));
        assertEquals(ErrorCode.ABI_PARSE_ERROR, e.getCode());
        assertTrue(e.getMessage().contains("constructor2"));
    }

    @Test
    void rejectsMissingElementType() {
        String abi = "[{\"name\":\"transfer\",\"inputs\":[]}]";
        AuditException e = assertThrows(AuditException.class, () -> parser.parse(abi));
        assertEquals(ErrorCode.ABI_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsUnknownAbiType() {
        String abi = "[{\"type\":\"function\",\"name\":\"f\",\"inputs\":[{\"name\":\"x\",\"type\":\"uint7\"}],\"outputs\":[]}]";
        AuditException e = assertThrows(AuditException.class, () -> parser.parse(abi));
        assertEquals(ErrorCode.ABI_PARSE_ERROR, e.getCode());
    }

    @Test
    void rejectsCompletelyUnknownType() {
        String abi = "[{\"type\":\"function\",\"name\":\"f\",\"inputs\":[{\"name\":\"x\",\"type\":\"foobar\"}],\"outputs\":[]}]";
        AuditException e = assertThrows(AuditException.class, () -> parser.parse(abi));
        assertEquals(ErrorCode.ABI_PARSE_ERROR, e.getCode());
        assertTrue(e.getMessage().contains("foobar"));
    }

    @Test
    void canonicalizesTuplesAndArrays() {
        String abi = """
                [{"type":"function","name":"f","stateMutability":"view",
                  "inputs":[{"name":"t","type":"tuple[2]","components":[
                      {"name":"a","type":"uint"},{"name":"b","type":"address[]"}]}],
                  "outputs":[{"name":"","type":"uint"}]}]
                """;
        ContractAbi parsed = parser.parse(abi);
        assertEquals("f((uint256,address[])[2])", parsed.functions().get(0).canonicalSignature());
        assertEquals("uint256", parsed.functions().get(0).outputs().get(0).type());
    }
}
