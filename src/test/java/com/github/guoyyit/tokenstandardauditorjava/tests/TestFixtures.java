package com.github.guoyyit.tokenstandardauditorjava.tests;

import com.github.guoyyit.tokenstandardauditorjava.util.Keccak256;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared test fixtures: standard ABIs and synthetic bytecode containing
 * exactly the selectors/topics a compliant contract would reference.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static final List<String> ERC20_FUNCTION_SIGS = List.of(
            "name()", "symbol()", "decimals()", "totalSupply()", "balanceOf(address)",
            "transfer(address,uint256)", "transferFrom(address,address,uint256)",
            "approve(address,uint256)", "allowance(address,address)");

    public static final List<String> ERC20_EVENT_SIGS = List.of(
            "Transfer(address,address,uint256)", "Approval(address,address,uint256)");

    public static final List<String> ERC721_FUNCTION_SIGS = List.of(
            "balanceOf(address)", "ownerOf(uint256)",
            "safeTransferFrom(address,address,uint256)",
            "safeTransferFrom(address,address,uint256,bytes)",
            "transferFrom(address,address,uint256)", "approve(address,uint256)",
            "getApproved(uint256)", "setApprovalForAll(address,bool)",
            "isApprovedForAll(address,address)", "supportsInterface(bytes4)");

    public static final List<String> ERC721_EVENT_SIGS = List.of(
            "Transfer(address,address,uint256)", "Approval(address,address,uint256)",
            "ApprovalForAll(address,address,bool)");

    public static String selector(String canonicalSignature) {
        return Keccak256.functionSelector(canonicalSignature);
    }

    public static String topic(String canonicalSignature) {
        return Keccak256.hexDigest(canonicalSignature);
    }

    /** Builds synthetic bytecode embedding the given selectors and event topics. */
    public static String bytecode(Collection<String> selectors, Collection<String> topics) {
        StringBuilder sb = new StringBuilder();
        for (String selector : selectors) {
            sb.append("63").append(selector);
        }
        for (String topic : topics) {
            sb.append("7f").append(topic);
        }
        sb.append("00");
        return sb.toString();
    }

    public static String erc20Bytecode() {
        return bytecode(ERC20_FUNCTION_SIGS.stream().map(TestFixtures::selector).toList(),
                ERC20_EVENT_SIGS.stream().map(TestFixtures::topic).toList());
    }

    public static String erc721Bytecode() {
        return bytecode(ERC721_FUNCTION_SIGS.stream().map(TestFixtures::selector).toList(),
                ERC721_EVENT_SIGS.stream().map(TestFixtures::topic).toList());
    }

    public static String erc20BytecodeWithout(String... excludedSignatures) {
        List<String> excluded = List.of(excludedSignatures);
        List<String> selectors = ERC20_FUNCTION_SIGS.stream()
                .filter(sig -> !excluded.contains(sig)).map(TestFixtures::selector).toList();
        List<String> topics = ERC20_EVENT_SIGS.stream()
                .filter(sig -> !excluded.contains(sig)).map(TestFixtures::topic).toList();
        return bytecode(selectors, topics);
    }

    public static final String ERC20_ABI = """
            [
              {"type":"function","name":"name","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"string"}]},
              {"type":"function","name":"symbol","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"string"}]},
              {"type":"function","name":"decimals","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"uint8"}]},
              {"type":"function","name":"totalSupply","stateMutability":"view","inputs":[],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"balanceOf","stateMutability":"view","inputs":[{"name":"account","type":"address"}],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"transfer","stateMutability":"nonpayable","inputs":[{"name":"to","type":"address"},{"name":"value","type":"uint256"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"transferFrom","stateMutability":"nonpayable","inputs":[{"name":"from","type":"address"},{"name":"to","type":"address"},{"name":"value","type":"uint256"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"approve","stateMutability":"nonpayable","inputs":[{"name":"spender","type":"address"},{"name":"value","type":"uint256"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"allowance","stateMutability":"view","inputs":[{"name":"owner","type":"address"},{"name":"spender","type":"address"}],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"event","name":"Transfer","inputs":[{"name":"from","type":"address","indexed":true},{"name":"to","type":"address","indexed":true},{"name":"value","type":"uint256","indexed":false}]},
              {"type":"event","name":"Approval","inputs":[{"name":"owner","type":"address","indexed":true},{"name":"spender","type":"address","indexed":true},{"name":"value","type":"uint256","indexed":false}]}
            ]
            """;

    public static final String ERC721_ABI = """
            [
              {"type":"function","name":"balanceOf","stateMutability":"view","inputs":[{"name":"owner","type":"address"}],"outputs":[{"name":"","type":"uint256"}]},
              {"type":"function","name":"ownerOf","stateMutability":"view","inputs":[{"name":"tokenId","type":"uint256"}],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"safeTransferFrom","stateMutability":"payable","inputs":[{"name":"from","type":"address"},{"name":"to","type":"address"},{"name":"tokenId","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"safeTransferFrom","stateMutability":"payable","inputs":[{"name":"from","type":"address"},{"name":"to","type":"address"},{"name":"tokenId","type":"uint256"},{"name":"data","type":"bytes"}],"outputs":[]},
              {"type":"function","name":"transferFrom","stateMutability":"payable","inputs":[{"name":"from","type":"address"},{"name":"to","type":"address"},{"name":"tokenId","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"approve","stateMutability":"payable","inputs":[{"name":"approved","type":"address"},{"name":"tokenId","type":"uint256"}],"outputs":[]},
              {"type":"function","name":"getApproved","stateMutability":"view","inputs":[{"name":"tokenId","type":"uint256"}],"outputs":[{"name":"","type":"address"}]},
              {"type":"function","name":"setApprovalForAll","stateMutability":"nonpayable","inputs":[{"name":"operator","type":"address"},{"name":"approved","type":"bool"}],"outputs":[]},
              {"type":"function","name":"isApprovedForAll","stateMutability":"view","inputs":[{"name":"owner","type":"address"},{"name":"operator","type":"address"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"function","name":"supportsInterface","stateMutability":"view","inputs":[{"name":"interfaceId","type":"bytes4"}],"outputs":[{"name":"","type":"bool"}]},
              {"type":"event","name":"Transfer","inputs":[{"name":"from","type":"address","indexed":true},{"name":"to","type":"address","indexed":true},{"name":"tokenId","type":"uint256","indexed":true}]},
              {"type":"event","name":"Approval","inputs":[{"name":"owner","type":"address","indexed":true},{"name":"approved","type":"address","indexed":true},{"name":"tokenId","type":"uint256","indexed":true}]},
              {"type":"event","name":"ApprovalForAll","inputs":[{"name":"owner","type":"address","indexed":true},{"name":"operator","type":"address","indexed":true},{"name":"approved","type":"bool","indexed":false}]}
            ]
            """;

    /** ERC-20 ABI with the Transfer event's "value" param wrongly indexed. */
    public static String erc20AbiWithBadEventIndexing() {
        String result = ERC20_ABI.replace(
                "\"name\":\"spender\",\"type\":\"address\",\"indexed\":true},{\"name\":\"value\",\"type\":\"uint256\",\"indexed\":false}",
                "\"name\":\"spender\",\"type\":\"address\",\"indexed\":true},{\"name\":\"value\",\"type\":\"uint256\",\"indexed\":true}");
        if (result.equals(ERC20_ABI)) {
            throw new IllegalStateException("fixture replace did not match");
        }
        return result;
    }

    /** ERC-20 ABI with the transfer function entry removed. */
    public static String erc20AbiWithoutTransfer() {
        String result = ERC20_ABI.replace(
                "{\"type\":\"function\",\"name\":\"transfer\",\"stateMutability\":\"nonpayable\",\"inputs\":[{\"name\":\"to\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}]},",
                "");
        if (result.equals(ERC20_ABI)) {
            throw new IllegalStateException("fixture replace did not match");
        }
        return result;
    }

    public static Stream<String> allErc20Sigs() {
        return Stream.concat(ERC20_FUNCTION_SIGS.stream(), ERC20_EVENT_SIGS.stream());
    }
}
