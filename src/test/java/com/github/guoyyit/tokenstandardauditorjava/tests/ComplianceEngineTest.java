package com.github.guoyyit.tokenstandardauditorjava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiModels.ContractAbi;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiParser;
import com.github.guoyyit.tokenstandardauditorjava.bytecode.EvmBytecodeParser;
import com.github.guoyyit.tokenstandardauditorjava.bytecode.ParsedBytecode;
import com.github.guoyyit.tokenstandardauditorjava.domain.Finding;
import com.github.guoyyit.tokenstandardauditorjava.domain.Severity;
import com.github.guoyyit.tokenstandardauditorjava.engine.ComplianceEngine;
import com.github.guoyyit.tokenstandardauditorjava.rules.RuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplianceEngineTest {

    private static final AbiParser ABI_PARSER = new AbiParser();
    private static final EvmBytecodeParser BYTECODE_PARSER = new EvmBytecodeParser();
    private static final ComplianceEngine ENGINE = new ComplianceEngine();

    private static RuleDefinition erc20V1;
    private static RuleDefinition erc721V1;

    @BeforeAll
    static void loadRules() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = ComplianceEngineTest.class.getResourceAsStream("/rules/erc20-v1.json")) {
            erc20V1 = mapper.readValue(in, RuleDefinition.class);
        }
        try (InputStream in = ComplianceEngineTest.class.getResourceAsStream("/rules/erc721-v1.json")) {
            erc721V1 = mapper.readValue(in, RuleDefinition.class);
        }
    }

    private static List<Finding> evaluateErc20(String abiJson, String bytecodeHex) {
        ContractAbi abi = ABI_PARSER.parse(abiJson);
        ParsedBytecode bytecode = BYTECODE_PARSER.parse(bytecodeHex);
        return ENGINE.evaluate(erc20V1, abi, bytecode);
    }

    @Test
    void compliantErc20HasNoErrorFindings() {
        List<Finding> findings = evaluateErc20(TestFixtures.ERC20_ABI, TestFixtures.erc20Bytecode());
        assertTrue(findings.stream().noneMatch(f -> f.getSeverity() == Severity.ERROR),
                "expected no ERROR findings but got: " + findings);
    }

    @Test
    void compliantErc721HasNoErrorFindings() {
        ContractAbi abi = ABI_PARSER.parse(TestFixtures.ERC721_ABI);
        ParsedBytecode bytecode = BYTECODE_PARSER.parse(TestFixtures.erc721Bytecode());
        List<Finding> findings = ENGINE.evaluate(erc721V1, abi, bytecode);
        assertTrue(findings.stream().noneMatch(f -> f.getSeverity() == Severity.ERROR),
                "expected no ERROR findings but got: " + findings);
    }

    @Test
    void abiDeclaredButNotInBytecodeIsError() {
        List<Finding> findings = evaluateErc20(TestFixtures.ERC20_ABI,
                TestFixtures.erc20BytecodeWithout("transfer(address,uint256)"));
        List<Finding> errors = findings.stream()
                .filter(f -> f.getSeverity() == Severity.ERROR)
                .filter(f -> f.getMessage().contains("transfer(address,uint256)"))
                .filter(f -> f.getMessage().contains("not present in the bytecode"))
                .toList();
        assertEquals(1, errors.size(), "expected exactly one bytecode-mismatch error: " + findings);
        assertEquals("function:transfer(address,uint256)", errors.get(0).getRuleId());
        assertEquals("transfer(address,uint256)", errors.get(0).getLocation().getSignature());
    }

    @Test
    void missingRequiredFunctionInAbiIsError() {
        List<Finding> findings = evaluateErc20(TestFixtures.erc20AbiWithoutTransfer(),
                TestFixtures.erc20Bytecode());
        assertTrue(findings.stream().anyMatch(f -> f.getSeverity() == Severity.ERROR
                && f.getMessage().contains("required function transfer(address,uint256)")
                && f.getMessage().contains("not declared")), "findings: " + findings);
    }

    @Test
    void eventIndexedMismatchIsReported() {
        List<Finding> findings = evaluateErc20(TestFixtures.erc20AbiWithBadEventIndexing(),
                TestFixtures.erc20Bytecode());
        assertTrue(findings.stream().anyMatch(f -> f.getMessage().contains("indexed")
                && f.getMessage().contains("Approval")), "findings: " + findings);
    }

    @Test
    void eventNotInBytecodeIsError() {
        List<Finding> findings = evaluateErc20(TestFixtures.ERC20_ABI,
                TestFixtures.erc20BytecodeWithout("Transfer(address,address,uint256)"));
        assertTrue(findings.stream().anyMatch(f -> f.getSeverity() == Severity.ERROR
                && f.getMessage().contains("Transfer(address,address,uint256)")
                && f.getMessage().contains("not present in the bytecode")), "findings: " + findings);
    }

    @Test
    void returnTypeMismatchIsWarning() {
        String badAbi = TestFixtures.ERC20_ABI.replace(
                "\"name\":\"transfer\",\"stateMutability\":\"nonpayable\",\"inputs\":[{\"name\":\"to\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}]",
                "\"name\":\"transfer\",\"stateMutability\":\"nonpayable\",\"inputs\":[{\"name\":\"to\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"outputs\":[]");
        List<Finding> findings = evaluateErc20(badAbi, TestFixtures.erc20Bytecode());
        assertTrue(findings.stream().anyMatch(f -> f.getSeverity() == Severity.WARNING
                && f.getMessage().contains("return types")), "findings: " + findings);
    }

    @Test
    void evaluationIsDeterministic() {
        List<Finding> first = evaluateErc20(TestFixtures.ERC20_ABI,
                TestFixtures.erc20BytecodeWithout("approve(address,uint256)"));
        List<Finding> second = evaluateErc20(TestFixtures.ERC20_ABI,
                TestFixtures.erc20BytecodeWithout("approve(address,uint256)"));
        assertEquals(first.stream().map(Finding::getRuleId).toList(),
                second.stream().map(Finding::getRuleId).toList());
        assertEquals(first.stream().map(Finding::getMessage).toList(),
                second.stream().map(Finding::getMessage).toList());
        assertEquals(first.stream().map(Finding::getSeverity).toList(),
                second.stream().map(Finding::getSeverity).toList());
    }
}
