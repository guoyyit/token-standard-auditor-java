package com.github.guoyyit.tokenstandardauditorjava.rule.impl;

import com.github.guoyyit.tokenstandardauditorjava.model.Severity;
import com.github.guoyyit.tokenstandardauditorjava.rule.Rule;
import com.github.guoyyit.tokenstandardauditorjava.rule.RuleSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for the built-in rule sets: ERC-20, ERC-721 and ERC-1155.
 *
 * <p>Each rule set is versioned. The default version for every standard is {@code "1.0"}.
 */
public final class BuiltinRuleSets {

    public static final String ERC20 = "ERC20";
    public static final String ERC721 = "ERC721";
    public static final String ERC1155 = "ERC1155";
    public static final String DEFAULT_VERSION = "1.0";

    private BuiltinRuleSets() {
    }

    public static RuleSet erc20() {
        String standard = ERC20;
        String version = DEFAULT_VERSION;
        List<Rule> rules = new ArrayList<>();

        rules.add(new AbiBytecodeConsistencyRule("ERC20-ABI-001", Severity.ERROR,
                "Functions declared in the ABI must be implemented in the bytecode", standard, version));

        rules.add(new RequiredFunctionRule("ERC20-REQ-001", Severity.ERROR,
                "totalSupply() is required", standard, version, "totalSupply()"));
        rules.add(new RequiredFunctionRule("ERC20-REQ-002", Severity.ERROR,
                "balanceOf(address) is required", standard, version, "balanceOf(address)"));
        rules.add(new RequiredFunctionRule("ERC20-REQ-003", Severity.ERROR,
                "transfer(address,uint256) is required", standard, version, "transfer(address,uint256)"));
        rules.add(new RequiredFunctionRule("ERC20-REQ-004", Severity.ERROR,
                "transferFrom(address,address,uint256) is required", standard, version,
                "transferFrom(address,address,uint256)"));
        rules.add(new RequiredFunctionRule("ERC20-REQ-005", Severity.ERROR,
                "approve(address,uint256) is required", standard, version, "approve(address,uint256)"));
        rules.add(new RequiredFunctionRule("ERC20-REQ-006", Severity.ERROR,
                "allowance(address,address) is required", standard, version, "allowance(address,address)"));

        rules.add(new FunctionOutputRule("ERC20-RET-001", Severity.WARNING,
                "transfer should return bool", standard, version,
                "transfer(address,uint256)", List.of("bool")));
        rules.add(new FunctionOutputRule("ERC20-RET-002", Severity.WARNING,
                "transferFrom should return bool", standard, version,
                "transferFrom(address,address,uint256)", List.of("bool")));
        rules.add(new FunctionOutputRule("ERC20-RET-003", Severity.WARNING,
                "approve should return bool", standard, version,
                "approve(address,uint256)", List.of("bool")));

        rules.add(new FunctionMutabilityRule("ERC20-MUT-001", Severity.WARNING,
                "totalSupply should be view", standard, version, "totalSupply()", "view"));
        rules.add(new FunctionMutabilityRule("ERC20-MUT-002", Severity.WARNING,
                "balanceOf should be view", standard, version, "balanceOf(address)", "view"));
        rules.add(new FunctionMutabilityRule("ERC20-MUT-003", Severity.WARNING,
                "allowance should be view", standard, version, "allowance(address,address)", "view"));

        rules.add(new RequiredEventRule("ERC20-EVT-001", Severity.ERROR,
                "Transfer event is required with indexed from and to", standard, version,
                "Transfer(address,address,uint256)", List.of(true, true, false)));
        rules.add(new RequiredEventRule("ERC20-EVT-002", Severity.ERROR,
                "Approval event is required with indexed owner and spender", standard, version,
                "Approval(address,address,uint256)", List.of(true, true, false)));

        rules.add(new OptionalFunctionRule("ERC20-OPT-001", Severity.WARNING,
                "name() is recommended", standard, version, "name()"));
        rules.add(new OptionalFunctionRule("ERC20-OPT-002", Severity.WARNING,
                "symbol() is recommended", standard, version, "symbol()"));
        rules.add(new OptionalFunctionRule("ERC20-OPT-003", Severity.WARNING,
                "decimals() is recommended", standard, version, "decimals()"));

        return new RuleSet(standard, version, "ERC-20 Fungible Token Standard", rules);
    }

    public static RuleSet erc721() {
        String standard = ERC721;
        String version = DEFAULT_VERSION;
        List<Rule> rules = new ArrayList<>();

        rules.add(new AbiBytecodeConsistencyRule("ERC721-ABI-001", Severity.ERROR,
                "Functions declared in the ABI must be implemented in the bytecode", standard, version));

        rules.add(new RequiredFunctionRule("ERC721-REQ-001", Severity.ERROR,
                "balanceOf(address) is required", standard, version, "balanceOf(address)"));
        rules.add(new RequiredFunctionRule("ERC721-REQ-002", Severity.ERROR,
                "ownerOf(uint256) is required", standard, version, "ownerOf(uint256)"));
        rules.add(new RequiredFunctionRule("ERC721-REQ-003", Severity.ERROR,
                "safeTransferFrom(address,address,uint256) is required", standard, version,
                "safeTransferFrom(address,address,uint256)"));
        rules.add(new RequiredFunctionRule("ERC721-REQ-004", Severity.ERROR,
                "transferFrom(address,address,uint256) is required", standard, version,
                "transferFrom(address,address,uint256)"));
        rules.add(new RequiredFunctionRule("ERC721-REQ-005", Severity.ERROR,
                "approve(address,uint256) is required", standard, version, "approve(address,uint256)"));
        rules.add(new RequiredFunctionRule("ERC721-REQ-006", Severity.ERROR,
                "setApprovalForAll(address,bool) is required", standard, version,
                "setApprovalForAll(address,bool)"));
        rules.add(new RequiredFunctionRule("ERC721-REQ-007", Severity.ERROR,
                "getApproved(uint256) is required", standard, version, "getApproved(uint256)"));
        rules.add(new RequiredFunctionRule("ERC721-REQ-008", Severity.ERROR,
                "isApprovedForAll(address,address) is required", standard, version,
                "isApprovedForAll(address,address)"));
        rules.add(new RequiredFunctionRule("ERC721-REQ-009", Severity.ERROR,
                "safeTransferFrom(address,address,uint256,bytes) is required", standard, version,
                "safeTransferFrom(address,address,uint256,bytes)"));

        rules.add(new FunctionOutputRule("ERC721-RET-001", Severity.WARNING,
                "transferFrom should return nothing", standard, version,
                "transferFrom(address,address,uint256)", List.of()));
        rules.add(new FunctionOutputRule("ERC721-RET-002", Severity.WARNING,
                "approve should return nothing", standard, version,
                "approve(address,uint256)", List.of()));
        rules.add(new FunctionOutputRule("ERC721-RET-003", Severity.WARNING,
                "setApprovalForAll should return nothing", standard, version,
                "setApprovalForAll(address,bool)", List.of()));
        rules.add(new FunctionOutputRule("ERC721-RET-004", Severity.WARNING,
                "isApprovedForAll should return bool", standard, version,
                "isApprovedForAll(address,address)", List.of("bool")));

        rules.add(new RequiredEventRule("ERC721-EVT-001", Severity.ERROR,
                "Transfer event is required with indexed from, to and tokenId", standard, version,
                "Transfer(address,address,uint256)", List.of(true, true, true)));
        rules.add(new RequiredEventRule("ERC721-EVT-002", Severity.ERROR,
                "Approval event is required with indexed owner, approved and tokenId", standard, version,
                "Approval(address,address,uint256)", List.of(true, true, true)));
        rules.add(new RequiredEventRule("ERC721-EVT-003", Severity.ERROR,
                "ApprovalForAll event is required with indexed owner and operator", standard, version,
                "ApprovalForAll(address,address,bool)", List.of(true, true, false)));

        rules.add(new OptionalFunctionRule("ERC721-OPT-001", Severity.WARNING,
                "name() is recommended", standard, version, "name()"));
        rules.add(new OptionalFunctionRule("ERC721-OPT-002", Severity.WARNING,
                "symbol() is recommended", standard, version, "symbol()"));
        rules.add(new OptionalFunctionRule("ERC721-OPT-003", Severity.WARNING,
                "tokenURI(uint256) is recommended", standard, version, "tokenURI(uint256)"));

        return new RuleSet(standard, version, "ERC-721 Non-Fungible Token Standard", rules);
    }

    public static RuleSet erc1155() {
        String standard = ERC1155;
        String version = DEFAULT_VERSION;
        List<Rule> rules = new ArrayList<>();

        rules.add(new AbiBytecodeConsistencyRule("ERC1155-ABI-001", Severity.ERROR,
                "Functions declared in the ABI must be implemented in the bytecode", standard, version));

        rules.add(new RequiredFunctionRule("ERC1155-REQ-001", Severity.ERROR,
                "safeTransferFrom(address,address,uint256,uint256,bytes) is required", standard, version,
                "safeTransferFrom(address,address,uint256,uint256,bytes)"));
        rules.add(new RequiredFunctionRule("ERC1155-REQ-002", Severity.ERROR,
                "safeBatchTransferFrom(address,address,uint256[],uint256[],bytes) is required", standard, version,
                "safeBatchTransferFrom(address,address,uint256[],uint256[],bytes)"));
        rules.add(new RequiredFunctionRule("ERC1155-REQ-003", Severity.ERROR,
                "balanceOf(address,uint256) is required", standard, version,
                "balanceOf(address,uint256)"));
        rules.add(new RequiredFunctionRule("ERC1155-REQ-004", Severity.ERROR,
                "balanceOfBatch(address[],uint256[]) is required", standard, version,
                "balanceOfBatch(address[],uint256[])"));
        rules.add(new RequiredFunctionRule("ERC1155-REQ-005", Severity.ERROR,
                "setApprovalForAll(address,bool) is required", standard, version,
                "setApprovalForAll(address,bool)"));
        rules.add(new RequiredFunctionRule("ERC1155-REQ-006", Severity.ERROR,
                "isApprovedForAll(address,address) is required", standard, version,
                "isApprovedForAll(address,address)"));

        rules.add(new FunctionOutputRule("ERC1155-RET-001", Severity.WARNING,
                "safeTransferFrom should return nothing", standard, version,
                "safeTransferFrom(address,address,uint256,uint256,bytes)", List.of()));
        rules.add(new FunctionOutputRule("ERC1155-RET-002", Severity.WARNING,
                "safeBatchTransferFrom should return nothing", standard, version,
                "safeBatchTransferFrom(address,address,uint256[],uint256[],bytes)", List.of()));
        rules.add(new FunctionOutputRule("ERC1155-RET-003", Severity.WARNING,
                "setApprovalForAll should return nothing", standard, version,
                "setApprovalForAll(address,bool)", List.of()));
        rules.add(new FunctionOutputRule("ERC1155-RET-004", Severity.WARNING,
                "isApprovedForAll should return bool", standard, version,
                "isApprovedForAll(address,address)", List.of("bool")));
        rules.add(new FunctionOutputRule("ERC1155-RET-005", Severity.WARNING,
                "balanceOf should return uint256", standard, version,
                "balanceOf(address,uint256)", List.of("uint256")));
        rules.add(new FunctionOutputRule("ERC1155-RET-006", Severity.WARNING,
                "balanceOfBatch should return uint256[]", standard, version,
                "balanceOfBatch(address[],uint256[])", List.of("uint256[]")));

        rules.add(new RequiredEventRule("ERC1155-EVT-001", Severity.ERROR,
                "TransferSingle event is required with indexed operator, from and to", standard, version,
                "TransferSingle(address,address,address,uint256,uint256)",
                List.of(true, true, true, false, false)));
        rules.add(new RequiredEventRule("ERC1155-EVT-002", Severity.ERROR,
                "TransferBatch event is required with indexed operator, from and to", standard, version,
                "TransferBatch(address,address,address,uint256[],uint256[])",
                List.of(true, true, true, false, false)));
        rules.add(new RequiredEventRule("ERC1155-EVT-003", Severity.ERROR,
                "ApprovalForAll event is required with indexed owner and operator", standard, version,
                "ApprovalForAll(address,address,bool)", List.of(true, true, false)));
        rules.add(new RequiredEventRule("ERC1155-EVT-004", Severity.ERROR,
                "URI event is required with indexed id", standard, version,
                "URI(string,uint256)", List.of(false, true)));

        return new RuleSet(standard, version, "ERC-1155 Multi Token Standard", rules);
    }
}
