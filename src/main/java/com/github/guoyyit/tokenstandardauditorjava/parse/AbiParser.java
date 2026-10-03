package com.github.guoyyit.tokenstandardauditorjava.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.tokenstandardauditorjava.model.AbiEvent;
import com.github.guoyyit.tokenstandardauditorjava.model.AbiFunction;
import com.github.guoyyit.tokenstandardauditorjava.model.AbiParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses a contract ABI (JSON array) into structured {@link AbiFunction} and {@link AbiEvent}
 * objects, computing canonical signatures and Keccak selectors/topics.
 *
 * <p>Malformed JSON, unknown ABI item types and unrecognized Solidity types are reported via
 * {@link ParseException} with code {@link ParseException#ABI_PARSE_ERROR}.
 */
@Component
public class AbiParser {

    private static final Pattern UINT_PATTERN = Pattern.compile("uint(\\d*)");
    private static final Pattern INT_PATTERN = Pattern.compile("int(\\d*)");
    private static final Pattern BYTES_N_PATTERN = Pattern.compile("bytes(\\d+)");
    private static final Pattern ARRAY_SUFFIX_PATTERN = Pattern.compile("(\\[\\d*])+$");

    private final ObjectMapper objectMapper;

    public AbiParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses the given ABI JSON string.
     *
     * @param abiJson ABI as a JSON array string
     * @return parsed functions and events
     * @throws ParseException if the ABI cannot be parsed
     */
    public ParsedAbi parse(String abiJson) {
        if (abiJson == null || abiJson.isBlank()) {
            throw new ParseException(ParseException.ABI_PARSE_ERROR, "ABI input is empty");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(abiJson);
        } catch (Exception e) {
            throw new ParseException(ParseException.ABI_PARSE_ERROR, "ABI is not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isArray()) {
            throw new ParseException(ParseException.ABI_PARSE_ERROR, "ABI must be a JSON array");
        }

        List<AbiFunction> functions = new ArrayList<>();
        List<AbiEvent> events = new ArrayList<>();
        for (JsonNode item : root) {
            String type = textOr(item, "type", "function");
            switch (type) {
                case "function" -> functions.add(parseFunction(item));
                case "event" -> events.add(parseEvent(item));
                case "constructor", "fallback", "receive", "error" -> {
                    // not part of the function/event interface checked by the auditor
                }
                default -> throw new ParseException(ParseException.ABI_PARSE_ERROR,
                        "Unknown ABI item type: " + type);
            }
        }
        return new ParsedAbi(functions, events);
    }

    private AbiFunction parseFunction(JsonNode item) {
        String name = textOr(item, "name", null);
        if (name == null || name.isBlank()) {
            throw new ParseException(ParseException.ABI_PARSE_ERROR, "Function is missing a name");
        }
        List<AbiParam> inputs = parseParams(item.path("inputs"), false);
        List<AbiParam> outputs = parseParams(item.path("outputs"), false);
        String stateMutability = textOr(item, "stateMutability", null);
        if (stateMutability == null) {
            boolean payable = item.path("payable").asBoolean(false);
            stateMutability = payable ? "payable" : "nonpayable";
        }
        String signature = signature(name, inputs);
        return new AbiFunction(name, inputs, outputs, stateMutability, signature,
                Keccak256.selector(signature));
    }

    private AbiEvent parseEvent(JsonNode item) {
        String name = textOr(item, "name", null);
        if (name == null || name.isBlank()) {
            throw new ParseException(ParseException.ABI_PARSE_ERROR, "Event is missing a name");
        }
        List<AbiParam> inputs = parseParams(item.path("inputs"), true);
        String signature = signature(name, inputs);
        return new AbiEvent(name, inputs, signature, Keccak256.topic(signature));
    }

    private List<AbiParam> parseParams(JsonNode node, boolean eventParams) {
        List<AbiParam> params = new ArrayList<>();
        if (node == null || node.isMissingNode()) {
            return params;
        }
        if (!node.isArray()) {
            throw new ParseException(ParseException.ABI_PARSE_ERROR, "ABI parameter list must be an array");
        }
        for (JsonNode paramNode : node) {
            params.add(parseParam(paramNode, eventParams));
        }
        return params;
    }

    private AbiParam parseParam(JsonNode node, boolean eventParam) {
        String name = textOr(node, "name", "");
        String type = textOr(node, "type", null);
        if (type == null) {
            throw new ParseException(ParseException.ABI_PARSE_ERROR, "ABI parameter is missing a type");
        }
        String canonicalType = canonicalType(node, type);
        boolean indexed = eventParam && node.path("indexed").asBoolean(false);
        return new AbiParam(name, type, canonicalType, indexed);
    }

    private String canonicalType(JsonNode node, String type) {
        String arraySuffix = "";
        String baseType = type;
        var arrayMatcher = ARRAY_SUFFIX_PATTERN.matcher(type);
        if (arrayMatcher.find()) {
            arraySuffix = arrayMatcher.group();
            baseType = type.substring(0, type.length() - arraySuffix.length());
        }
        String canonicalBase = switch (baseType) {
            case "address", "bool", "string", "bytes" -> baseType;
            case "tuple" -> canonicalTuple(node);
            default -> {
                var uintMatcher = UINT_PATTERN.matcher(baseType);
                if (uintMatcher.matches()) {
                    String bits = uintMatcher.group(1);
                    yield bits.isEmpty() ? "uint256" : "uint" + bits;
                }
                var intMatcher = INT_PATTERN.matcher(baseType);
                if (intMatcher.matches()) {
                    String bits = intMatcher.group(1);
                    yield bits.isEmpty() ? "int256" : "int" + bits;
                }
                var bytesNMatcher = BYTES_N_PATTERN.matcher(baseType);
                if (bytesNMatcher.matches()) {
                    yield "bytes" + bytesNMatcher.group(1);
                }
                throw new ParseException(ParseException.ABI_PARSE_ERROR, "Unknown Solidity type: " + type);
            }
        };
        return canonicalBase + arraySuffix;
    }

    private String canonicalTuple(JsonNode node) {
        JsonNode components = node.path("components");
        if (!components.isArray()) {
            throw new ParseException(ParseException.ABI_PARSE_ERROR, "Tuple type is missing components");
        }
        List<String> componentTypes = new ArrayList<>();
        for (JsonNode component : components) {
            componentTypes.add(canonicalType(component, textOr(component, "type", null)));
        }
        return "(" + String.join(",", componentTypes) + ")";
    }

    private static String signature(String name, List<AbiParam> params) {
        List<String> types = params.stream().map(AbiParam::canonicalType).toList();
        return name + "(" + String.join(",", types) + ")";
    }

    private static String textOr(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return value.asText(defaultValue);
    }

    /**
     * Result of parsing an ABI: the declared functions and events.
     */
    public record ParsedAbi(List<AbiFunction> functions, List<AbiEvent> events) {
    }
}
