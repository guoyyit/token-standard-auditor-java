package com.github.guoyyit.tokenstandardauditorjava.fixtures;

import com.github.guoyyit.tokenstandardauditorjava.parse.Keccak256;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds synthetic contract ABI and bytecode for tests.
 *
 * <p>Bytecode is a minimal linear sequence of {@code PUSH4 <selector>} and {@code PUSH32 <topic>}
 * instructions, which is sufficient for the parser to extract all selectors and topics.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static String selector(String signature) {
        return Keccak256.selector(signature);
    }

    public static String topic(String signature) {
        return Keccak256.topic(signature);
    }

    /**
     * Builds bytecode containing the given function selectors and event topics.
     */
    public static String bytecodeOf(List<String> functionSignatures, List<String> eventSignatures) {
        StringBuilder sb = new StringBuilder("0x");
        for (String sig : functionSignatures) {
            sb.append("63").append(selector(sig).substring(2));
        }
        for (String sig : eventSignatures) {
            sb.append("7f").append(topic(sig).substring(2));
        }
        return sb.toString();
    }

    public static List<String> erc20Functions() {
        return new ArrayList<>(List.of(
                "totalSupply()",
                "balanceOf(address)",
                "transfer(address,uint256)",
                "transferFrom(address,address,uint256)",
                "approve(address,uint256)",
                "allowance(address,address)"));
    }

    public static List<String> erc20Events() {
        return new ArrayList<>(List.of(
                "Transfer(address,address,uint256)",
                "Approval(address,address,uint256)"));
    }

    public static List<String> erc721Functions() {
        return new ArrayList<>(List.of(
                "balanceOf(address)",
                "ownerOf(uint256)",
                "safeTransferFrom(address,address,uint256)",
                "transferFrom(address,address,uint256)",
                "approve(address,uint256)",
                "setApprovalForAll(address,bool)",
                "getApproved(uint256)",
                "isApprovedForAll(address,address)",
                "safeTransferFrom(address,address,uint256,bytes)"));
    }

    public static List<String> erc721Events() {
        return new ArrayList<>(List.of(
                "Transfer(address,address,uint256)",
                "Approval(address,address,uint256)",
                "ApprovalForAll(address,address,bool)"));
    }

    public static List<String> erc1155Functions() {
        return new ArrayList<>(List.of(
                "safeTransferFrom(address,address,uint256,uint256,bytes)",
                "safeBatchTransferFrom(address,address,uint256[],uint256[],bytes)",
                "balanceOf(address,uint256)",
                "balanceOfBatch(address[],uint256[])",
                "setApprovalForAll(address,bool)",
                "isApprovedForAll(address,address)"));
    }

    public static List<String> erc1155Events() {
        return new ArrayList<>(List.of(
                "TransferSingle(address,address,address,uint256,uint256)",
                "TransferBatch(address,address,address,uint256[],uint256[])",
                "ApprovalForAll(address,address,bool)",
                "URI(string,uint256)"));
    }
}
