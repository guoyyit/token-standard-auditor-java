package com.github.guoyyit.tokenstandardauditorjava.abi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiModels.AbiEvent;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiModels.AbiFunction;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiModels.AbiParam;
import com.github.guoyyit.tokenstandardauditorjava.abi.AbiModels.ContractAbi;
import com.github.guoyyit.tokenstandardauditorjava.api.AuditException;
import com.github.guoyyit.tokenstandardauditorjava.api.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a contract ABI JSON document into {@link ContractAbi}.
 * Unknown element types or unknown ABI types are reported as errors,
 * never silently skipped.
 */
@Component
public class AbiParser {

    private static final Set<String> KNOWN_ELEMENT_TYPES =
            Set.of("function", "event", "constructor", "fallback", "receive", "error");

    private static final Set<String> KNOWN_MUTABILITY =
            Set.of("pure", "view", "nonpayable", "payable");

    private static final Pattern INT_TYPE = Pattern.compile("^(u?int)(\\d{1,3})?$");
    private static final Pattern BYTES_FIXED = Pattern.compile("^bytes(\\d{1,2})$");
    private static final Pattern FIXED_TYPE = Pattern.compile("^(u?fixed)(\\d{1,3}x\\d{1,2})?$");
    private static final Pattern ARRAY_SUFFIX = Pattern.compile("\\[(\\d*)]$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContractAbi parse(String abiJson) {
        if (abiJson == null || abiJson.isBlank()) {
            throw new AuditException(ErrorCode.ABI_PARSE_ERROR, "ABI JSON is missing");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(abiJson);
        } catch (Exception e) {
            throw new AuditException(ErrorCode.ABI_PARSE_ERROR, "ABI is not valid JSON: " + e.getMessage(), e);
        }
        if (!root.isArray()) {
            throw new AuditException(ErrorCode.ABI_PARSE_ERROR, "ABI root must be a JSON array");
        }

        List<AbiFunction> functions = new ArrayList<>();
        List<AbiEvent> events = new ArrayList<>();
        int index = 0;
        for (JsonNode element : root) {
            String where = "ABI element #" + index;
            index++;
            if (!element.isObject()) {
                throw new AuditException(ErrorCode.ABI_PARSE_ERROR, where + " is not an object");
            }
            JsonNode typeNode = element.get("type");
            if (typeNode == null || !typeNode.isTextual()) {
                throw new AuditException(ErrorCode.ABI_PARSE_ERROR, where + " is missing its \"type\" field");
            }
            String type = typeNode.asText();
            if (!KNOWN_ELEMENT_TYPES.contains(type)) {
                throw new AuditException(ErrorCode.ABI_PARSE_ERROR,
                        where + " has unknown type \"" + type + "\"");
            }
            switch (type) {
                case "function" -> functions.add(parseFunction(element, where));
                case "event" -> events.add(parseEvent(element, where));
                default -> {
                    // constructor/fallback/receive/error: valid but not needed for compliance checks
                }
            }
        }
        return new ContractAbi(List.copyOf(functions), List.copyOf(events));
    }

    private AbiFunction parseFunction(JsonNode element, String where) {
        String name = requiredName(element, where);
        List<AbiParam> inputs = parseParams(element.get("inputs"), false, where + " inputs");
        List<AbiParam> outputs = parseParams(element.get("outputs"), false, where + " outputs");
        String mutability = null;
        JsonNode mutabilityNode = element.get("stateMutability");
        if (mutabilityNode != null && !mutabilityNode.isNull()) {
            if (!mutabilityNode.isTextual() || !KNOWN_MUTABILITY.contains(mutabilityNode.asText())) {
                throw new AuditException(ErrorCode.ABI_PARSE_ERROR,
                        where + " has unknown stateMutability");
            }
            mutability = mutabilityNode.asText();
        }
        return new AbiFunction(name, inputs, outputs, mutability);
    }

    private AbiEvent parseEvent(JsonNode element, String where) {
        String name = requiredName(element, where);
        List<AbiParam> inputs = parseParams(element.get("inputs"), true, where + " inputs");
        return new AbiEvent(name, inputs);
    }

    private String requiredName(JsonNode element, String where) {
        JsonNode nameNode = element.get("name");
        if (nameNode == null || !nameNode.isTextual() || nameNode.asText().isEmpty()) {
            throw new AuditException(ErrorCode.ABI_PARSE_ERROR, where + " is missing its \"name\" field");
        }
        return nameNode.asText();
    }

    private List<AbiParam> parseParams(JsonNode paramsNode, boolean eventParams, String where) {
        List<AbiParam> params = new ArrayList<>();
        if (paramsNode == null || paramsNode.isNull()) {
            return params;
        }
        if (!paramsNode.isArray()) {
            throw new AuditException(ErrorCode.ABI_PARSE_ERROR, where + " must be an array");
        }
        int index = 0;
        for (JsonNode param : paramsNode) {
            String paramWhere = where + " param #" + index;
            index++;
            if (!param.isObject()) {
                throw new AuditException(ErrorCode.ABI_PARSE_ERROR, paramWhere + " is not an object");
            }
            JsonNode typeNode = param.get("type");
            if (typeNode == null || !typeNode.isTextual()) {
                throw new AuditException(ErrorCode.ABI_PARSE_ERROR, paramWhere + " is missing its \"type\" field");
            }
            String rawType = typeNode.asText();
            List<AbiParam> components = List.of();
            if (baseType(rawType).equals("tuple")) {
                components = parseParams(param.get("components"), false, paramWhere + " components");
                if (components.isEmpty()) {
                    throw new AuditException(ErrorCode.ABI_PARSE_ERROR,
                            paramWhere + " declares tuple type without components");
                }
            }
            String canonicalType = canonicalType(rawType, components);
            JsonNode nameNode = param.get("name");
            String name = nameNode != null && nameNode.isTextual() ? nameNode.asText() : "";
            boolean indexed = eventParams && param.has("indexed") && param.get("indexed").asBoolean(false);
            params.add(new AbiParam(name, canonicalType, indexed, components));
        }
        return params;
    }

    /**
     * Returns the canonical type string (tuples expanded recursively),
     * validating the base type and array suffixes along the way.
     */
    private String canonicalType(String rawType, List<AbiParam> components) {
        String base = baseType(rawType);
        validateBaseType(base, rawType);
        String canonicalBase;
        if (base.equals("tuple")) {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < components.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(components.get(i).type());
            }
            sb.append(')');
            canonicalBase = sb.toString();
        } else {
            canonicalBase = switch (base) {
                case "uint" -> "uint256";
                case "int" -> "int256";
                case "fixed" -> "fixed128x18";
                case "ufixed" -> "ufixed128x18";
                default -> base;
            };
        }
        String suffixes = rawType.substring(base.length());
        validateArraySuffixes(suffixes, rawType);
        return canonicalBase + suffixes;
    }

    private String baseType(String rawType) {
        String base = rawType;
        while (base.endsWith("]")) {
            int open = base.lastIndexOf('[');
            if (open < 0) {
                throw new AuditException(ErrorCode.ABI_PARSE_ERROR, "malformed ABI type: " + rawType);
            }
            base = base.substring(0, open);
        }
        return base;
    }

    private void validateArraySuffixes(String suffixes, String rawType) {
        String rest = suffixes;
        while (!rest.isEmpty()) {
            Matcher m = ARRAY_SUFFIX.matcher(rest);
            if (!m.find()) {
                throw new AuditException(ErrorCode.ABI_PARSE_ERROR, "malformed ABI type: " + rawType);
            }
            rest = rest.substring(0, rest.length() - m.group(0).length());
        }
    }

    private void validateBaseType(String base, String rawType) {
        switch (base) {
            case "address", "bool", "string", "bytes", "function", "tuple" -> {
                return;
            }
            default -> {
            }
        }
        Matcher intMatcher = INT_TYPE.matcher(base);
        if (intMatcher.matches()) {
            if (intMatcher.group(2) != null) {
                int bits = Integer.parseInt(intMatcher.group(2));
                if (bits < 8 || bits > 256 || bits % 8 != 0) {
                    throw new AuditException(ErrorCode.ABI_PARSE_ERROR, "invalid integer width in type: " + rawType);
                }
            }
            return;
        }
        Matcher bytesMatcher = BYTES_FIXED.matcher(base);
        if (bytesMatcher.matches()) {
            int size = Integer.parseInt(bytesMatcher.group(1));
            if (size < 1 || size > 32) {
                throw new AuditException(ErrorCode.ABI_PARSE_ERROR, "invalid bytesN size in type: " + rawType);
            }
            return;
        }
        if (FIXED_TYPE.matcher(base).matches()) {
            return;
        }
        throw new AuditException(ErrorCode.ABI_PARSE_ERROR, "unknown ABI type: " + rawType);
    }
}
