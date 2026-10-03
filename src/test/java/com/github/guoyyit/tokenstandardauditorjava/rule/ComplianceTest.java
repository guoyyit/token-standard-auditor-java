package com.github.guoyyit.tokenstandardauditorjava.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.tokenstandardauditorjava.fixtures.AbiBuilder;
import com.github.guoyyit.tokenstandardauditorjava.fixtures.TestFixtures;
import com.github.guoyyit.tokenstandardauditorjava.model.BytecodeInfo;
import com.github.guoyyit.tokenstandardauditorjava.model.ContractContract;
import com.github.guoyyit.tokenstandardauditorjava.model.Finding;
import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import com.github.guoyyit.tokenstandardauditorjava.parse.AbiParser;
import com.github.guoyyit.tokenstandardauditorjava.parse.BytecodeParser;
import com.github.guoyyit.tokenstandardauditorjava.rule.impl.BuiltinRuleSets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplianceTest {

    private AbiParser abiParser;
    private BytecodeParser bytecodeParser;
    private RuleRegistry registry;

    @BeforeEach
    void setUp() {
        abiParser = new AbiParser(new ObjectMapper());
        bytecodeParser = new BytecodeParser();
        registry = new RuleRegistry();
        registry.register(BuiltinRuleSets.erc20());
        registry.register(BuiltinRuleSets.erc721());
        registry.register(BuiltinRuleSets.erc1155());
    }

    private List<Finding> evaluate(String standard, String version, String abi, String bytecodeHex) {
        AbiParser.ParsedAbi parsedAbi = abiParser.parse(abi);
        BytecodeInfo bytecode = bytecodeParser.parse(bytecodeHex);
        ContractContract contract = new ContractContract(parsedAbi.functions(), parsedAbi.events(), bytecode);
        RuleSet ruleSet = registry.snapshot(standard, version);
        List<Finding> findings = new ArrayList<>();
        for (Rule rule : ruleSet.rules()) {
            findings.addAll(rule.evaluate(contract));
        }
        return findings;
    }

    private long countSeverity(List<Finding> findings, Severity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    @Test
    void fullErc20IsCompliant() {
        String abi = AbiBuilder.builder()
                .function("totalSupply()", "uint256")
                .function("balanceOf(address)", "uint256")
                .function("transfer(address,uint256)")
                .function("transferFrom(address,address,uint256)")
                .function("approve(address,uint256)")
                .function("allowance(address,address)", "uint256")
                .function("name()", "string")
                .function("symbol()", "string")
                .function("decimals()", "uint8")
                .event("Transfer(address,address,uint256)", true, true, false)
                .event("Approval(address,address,uint256)", true, true, false)
                .build();
        List<String> allFunctions = new ArrayList<>(TestFixtures.erc20Functions());
        allFunctions.addAll(List.of("name()", "symbol()", "decimals()"));
        String bytecode = TestFixtures.bytecodeOf(allFunctions, TestFixtures.erc20Events());

        List<Finding> findings = evaluate(BuiltinRuleSets.ERC20, BuiltinRuleSets.DEFAULT_VERSION, abi, bytecode);
        assertEquals(0, countSeverity(findings, Severity.ERROR));
    }

    @Test
    void erc20MissingRequiredFunctionIsError() {
        String abi = AbiBuilder.builder()
                .function("totalSupply()", "uint256")
                .function("balanceOf(address)", "uint256")
                // transfer missing
                .function("transferFrom(address,address,uint256)")
                .function("approve(address,uint256)")
                .function("allowance(address,address)", "uint256")
                .event("Transfer(address,address,uint256)", true, true, false)
                .event("Approval(address,address,uint256)", true, true, false)
                .build();
        // bytecode also lacks transfer
        List<String> functions = new ArrayList<>(TestFixtures.erc20Functions());
        functions.remove("transfer(address,uint256)");
        String bytecode = TestFixtures.bytecodeOf(functions, TestFixtures.erc20Events());

        List<Finding> findings = evaluate(BuiltinRuleSets.ERC20, BuiltinRuleSets.DEFAULT_VERSION, abi, bytecode);
        assertTrue(countSeverity(findings, Severity.ERROR) >= 1);
        assertTrue(findings.stream().anyMatch(f -> f.ruleId().equals("ERC20-REQ-003")));
    }

    @Test
    void erc20DeclaredButNotImplementedIsError() {
        String abi = AbiBuilder.builder()
                .function("totalSupply()", "uint256")
                .function("balanceOf(address)", "uint256")
                .function("transfer(address,uint256)")
                .function("transferFrom(address,address,uint256)")
                .function("approve(address,uint256)")
                .function("allowance(address,address)", "uint256")
                .event("Transfer(address,address,uint256)", true, true, false)
                .event("Approval(address,address,uint256)", true, true, false)
                .build();
        // bytecode implements everything EXCEPT transfer
        List<String> functions = new ArrayList<>(TestFixtures.erc20Functions());
        functions.remove("transfer(address,uint256)");
        String bytecode = TestFixtures.bytecodeOf(functions, TestFixtures.erc20Events());

        List<Finding> findings = evaluate(BuiltinRuleSets.ERC20, BuiltinRuleSets.DEFAULT_VERSION, abi, bytecode);
        assertTrue(findings.stream().anyMatch(f -> f.ruleId().equals("ERC20-ABI-001")));
        assertTrue(findings.stream().anyMatch(f -> f.ruleId().equals("ERC20-REQ-003")));
    }

    @Test
    void erc20WrongReturnConventionIsWarning() {
        String abi = AbiBuilder.builder()
                .function("totalSupply()", "uint256")
                .function("balanceOf(address)", "uint256")
                .function("transfer(address,uint256)", "uint256") // should return bool
                .function("transferFrom(address,address,uint256)")
                .function("approve(address,uint256)")
                .function("allowance(address,address)", "uint256")
                .event("Transfer(address,address,uint256)", true, true, false)
                .event("Approval(address,address,uint256)", true, true, false)
                .build();
        String bytecode = TestFixtures.bytecodeOf(TestFixtures.erc20Functions(), TestFixtures.erc20Events());

        List<Finding> findings = evaluate(BuiltinRuleSets.ERC20, BuiltinRuleSets.DEFAULT_VERSION, abi, bytecode);
        assertEquals(0, countSeverity(findings, Severity.ERROR));
        assertTrue(findings.stream().anyMatch(f -> f.ruleId().equals("ERC20-RET-001")));
    }

    @Test
    void erc20MissingIndexedEventParamsIsError() {
        String abi = AbiBuilder.builder()
                .function("totalSupply()", "uint256")
                .function("balanceOf(address)", "uint256")
                .function("transfer(address,uint256)")
                .function("transferFrom(address,address,uint256)")
                .function("approve(address,uint256)")
                .function("allowance(address,address)", "uint256")
                .event("Transfer(address,address,uint256)", false, false, false) // should be indexed
                .event("Approval(address,address,uint256)", true, true, false)
                .build();
        String bytecode = TestFixtures.bytecodeOf(TestFixtures.erc20Functions(), TestFixtures.erc20Events());

        List<Finding> findings = evaluate(BuiltinRuleSets.ERC20, BuiltinRuleSets.DEFAULT_VERSION, abi, bytecode);
        assertTrue(findings.stream().anyMatch(f -> f.ruleId().equals("ERC20-EVT-001")));
    }

    @Test
    void fullErc721IsCompliant() {
        String abi = AbiBuilder.builder()
                .function("balanceOf(address)", "uint256")
                .function("ownerOf(uint256)", "address")
                .function("safeTransferFrom(address,address,uint256)", "")
                .function("transferFrom(address,address,uint256)", "")
                .function("approve(address,uint256)", "")
                .function("setApprovalForAll(address,bool)", "")
                .function("getApproved(uint256)", "address")
                .function("isApprovedForAll(address,address)", "bool")
                .function("safeTransferFrom(address,address,uint256,bytes)", "")
                .function("name()", "string")
                .function("symbol()", "string")
                .function("tokenURI(uint256)", "string")
                .event("Transfer(address,address,uint256)", true, true, true)
                .event("Approval(address,address,uint256)", true, true, true)
                .event("ApprovalForAll(address,address,bool)", true, true, false)
                .build();
        List<String> allFunctions = new ArrayList<>(TestFixtures.erc721Functions());
        allFunctions.addAll(List.of("name()", "symbol()", "tokenURI(uint256)"));
        String bytecode = TestFixtures.bytecodeOf(allFunctions, TestFixtures.erc721Events());

        List<Finding> findings = evaluate(BuiltinRuleSets.ERC721, BuiltinRuleSets.DEFAULT_VERSION, abi, bytecode);
        assertEquals(0, countSeverity(findings, Severity.ERROR));
    }

    @Test
    void fullErc1155IsCompliant() {
        String abi = AbiBuilder.builder()
                .function("safeTransferFrom(address,address,uint256,uint256,bytes)", "")
                .function("safeBatchTransferFrom(address,address,uint256[],uint256[],bytes)", "")
                .function("balanceOf(address,uint256)", "uint256")
                .function("balanceOfBatch(address[],uint256[])", "uint256[]")
                .function("setApprovalForAll(address,bool)", "")
                .function("isApprovedForAll(address,address)", "bool")
                .event("TransferSingle(address,address,address,uint256,uint256)", true, true, true, false, false)
                .event("TransferBatch(address,address,address,uint256[],uint256[])", true, true, true, false, false)
                .event("ApprovalForAll(address,address,bool)", true, true, false)
                .event("URI(string,uint256)", false, true)
                .build();
        String bytecode = TestFixtures.bytecodeOf(TestFixtures.erc1155Functions(), TestFixtures.erc1155Events());

        List<Finding> findings = evaluate(BuiltinRuleSets.ERC1155, BuiltinRuleSets.DEFAULT_VERSION, abi, bytecode);
        assertEquals(0, countSeverity(findings, Severity.ERROR));
    }

    @Test
    void findingsAreDeterministic() {
        String abi = AbiBuilder.builder()
                .function("totalSupply()", "uint256")
                .function("balanceOf(address)", "uint256")
                .function("transfer(address,uint256)")
                .function("transferFrom(address,address,uint256)")
                .function("approve(address,uint256)")
                .function("allowance(address,address)", "uint256")
                .event("Transfer(address,address,uint256)", true, true, false)
                .event("Approval(address,address,uint256)", true, true, false)
                .build();
        String bytecode = TestFixtures.bytecodeOf(TestFixtures.erc20Functions(), TestFixtures.erc20Events());

        List<Finding> first = evaluate(BuiltinRuleSets.ERC20, BuiltinRuleSets.DEFAULT_VERSION, abi, bytecode);
        List<Finding> second = evaluate(BuiltinRuleSets.ERC20, BuiltinRuleSets.DEFAULT_VERSION, abi, bytecode);
        assertEquals(first, second);
    }
}
