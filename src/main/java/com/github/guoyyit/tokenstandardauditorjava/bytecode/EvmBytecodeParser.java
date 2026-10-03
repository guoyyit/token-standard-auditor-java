package com.github.guoyyit.tokenstandardauditorjava.bytecode;

import com.github.guoyyit.tokenstandardauditorjava.api.AuditException;
import com.github.guoyyit.tokenstandardauditorjava.api.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes hex-encoded EVM bytecode into a {@link ParsedBytecode} structure.
 */
@Component
public class EvmBytecodeParser {

    public ParsedBytecode parse(String bytecodeHex) {
        if (bytecodeHex == null || bytecodeHex.isBlank()) {
            throw new AuditException(ErrorCode.BYTECODE_PARSE_ERROR, "bytecode is missing");
        }
        String hex = bytecodeHex.trim();
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.length() % 2 != 0) {
            throw new AuditException(ErrorCode.BYTECODE_PARSE_ERROR,
                    "bytecode hex has odd length: " + hex.length());
        }
        byte[] code = new byte[hex.length() / 2];
        for (int i = 0; i < code.length; i++) {
            int hi = hexDigit(hex.charAt(i * 2));
            int lo = hexDigit(hex.charAt(i * 2 + 1));
            code[i] = (byte) ((hi << 4) | lo);
        }

        List<EvmOpcode> opcodes = new ArrayList<>();
        Map<String, List<Integer>> functionSelectors = new LinkedHashMap<>();
        Map<String, List<Integer>> eventTopics = new LinkedHashMap<>();

        int i = 0;
        while (i < code.length) {
            int opcode = code[i] & 0xff;
            if (opcode >= 0x60 && opcode <= 0x7f) {
                int pushSize = opcode - 0x5f;
                int available = Math.min(pushSize, code.length - i - 1);
                byte[] data = new byte[available];
                System.arraycopy(code, i + 1, data, 0, available);
                opcodes.add(new EvmOpcode(i, opcode, data));
                if (available == pushSize) {
                    String dataHex = toHex(data);
                    if (opcode == 0x63) {
                        functionSelectors.computeIfAbsent(dataHex, k -> new ArrayList<>()).add(i);
                    } else if (opcode == 0x7f) {
                        eventTopics.computeIfAbsent(dataHex, k -> new ArrayList<>()).add(i);
                    }
                }
                i += 1 + available;
            } else {
                opcodes.add(new EvmOpcode(i, opcode, new byte[0]));
                i += 1;
            }
        }
        return new ParsedBytecode(code, List.copyOf(opcodes), functionSelectors, eventTopics);
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        throw new AuditException(ErrorCode.BYTECODE_PARSE_ERROR, "invalid hex character: '" + c + "'");
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }
}
