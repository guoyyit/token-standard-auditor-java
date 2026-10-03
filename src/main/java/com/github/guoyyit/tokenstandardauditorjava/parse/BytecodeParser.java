package com.github.guoyyit.tokenstandardauditorjava.parse;

import com.github.guoyyit.tokenstandardauditorjava.model.BytecodeInfo;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Parses EVM bytecode into a {@link BytecodeInfo}, extracting function selectors and event topics.
 *
 * <p>The parser performs a linear-sweep disassembly, collecting {@code PUSH4} immediates (function
 * selectors) and {@code PUSH32} immediates (event topic hashes). It also strips the Solidity
 * metadata suffix so that metadata bytes are not mistaken for code.
 *
 * <p>Malformed bytecode is reported via {@link ParseException} with code
 * {@link ParseException#BYTECODE_PARSE_ERROR}.
 */
@Component
public class BytecodeParser {

    private static final int PUSH1 = 0x60;
    private static final int PUSH32 = 0x7f;
    private static final int PUSH4 = 0x63;

    /**
     * Parses the given bytecode hex string.
     *
     * @param bytecodeHex bytecode as a hex string (0x prefix optional)
     * @return parsed bytecode info
     * @throws ParseException if the bytecode cannot be parsed
     */
    public BytecodeInfo parse(String bytecodeHex) {
        if (bytecodeHex == null || bytecodeHex.isBlank()) {
            throw new ParseException(ParseException.BYTECODE_PARSE_ERROR, "Bytecode input is empty");
        }
        String normalized = normalize(bytecodeHex);
        byte[] bytes;
        try {
            bytes = hexToBytes(normalized);
        } catch (IllegalArgumentException e) {
            throw new ParseException(ParseException.BYTECODE_PARSE_ERROR, "Bytecode is not valid hex: " + e.getMessage(), e);
        }
        if (bytes.length == 0) {
            throw new ParseException(ParseException.BYTECODE_PARSE_ERROR, "Bytecode is empty");
        }

        MetadataStripped stripped = stripMetadata(bytes);
        Set<String> selectors = new HashSet<>();
        Set<String> topics = new HashSet<>();
        linearSweep(stripped.code(), selectors, topics);

        return new BytecodeInfo(
                "0x" + normalized,
                "0x" + bytesToHex(stripped.code()),
                selectors,
                topics,
                stripped.code().length,
                stripped.metadataPresent());
    }

    /**
     * Checks whether the given 4-byte selector appears anywhere in the code (raw byte search).
     * This is a fallback for selectors that are not pushed via {@code PUSH4}.
     */
    public boolean containsSelector(BytecodeInfo info, String selector) {
        return containsPattern(info.code(), selector);
    }

    /**
     * Checks whether the given 32-byte topic appears anywhere in the code (raw byte search).
     */
    public boolean containsTopic(BytecodeInfo info, String topic) {
        return containsPattern(info.code(), topic);
    }

    private boolean containsPattern(String codeHex, String patternHex) {
        String code = codeHex.startsWith("0x") ? codeHex.substring(2) : codeHex;
        String pattern = patternHex.startsWith("0x") ? patternHex.substring(2) : patternHex;
        if (pattern.length() % 2 != 0 || pattern.isEmpty()) {
            return false;
        }
        return code.contains(pattern.toLowerCase());
    }

    private void linearSweep(byte[] code, Set<String> selectors, Set<String> topics) {
        int i = 0;
        while (i < code.length) {
            int op = code[i] & 0xff;
            if (op >= PUSH1 && op <= PUSH32) {
                int immediateSize = op - PUSH1 + 1;
                int immediateStart = i + 1;
                int immediateEnd = immediateStart + immediateSize;
                if (immediateEnd <= code.length) {
                    if (immediateSize == 4) {
                        selectors.add("0x" + bytesToHex(code, immediateStart, immediateSize));
                    } else if (immediateSize == 32) {
                        topics.add("0x" + bytesToHex(code, immediateStart, immediateSize));
                    }
                }
                i = immediateEnd;
            } else {
                i++;
            }
        }
    }

    private MetadataStripped stripMetadata(byte[] bytes) {
        if (bytes.length < 2) {
            return new MetadataStripped(bytes, false);
        }
        int metadataLength = ((bytes[bytes.length - 2] & 0xff) << 8) | (bytes[bytes.length - 1] & 0xff);
        int codeEnd = bytes.length - 2 - metadataLength;
        if (codeEnd < 0 || codeEnd > bytes.length - 2) {
            return new MetadataStripped(bytes, false);
        }
        // The CBOR metadata blob starts with a map (0xa0-0xbf) or array (0x80-0x9f).
        int metadataStart = codeEnd;
        if (metadataStart < bytes.length - 2) {
            int first = bytes[metadataStart] & 0xff;
            boolean looksLikeCbor = (first >= 0xa0 && first <= 0xbf) || (first >= 0x80 && first <= 0x9f);
            if (!looksLikeCbor) {
                return new MetadataStripped(bytes, false);
            }
        }
        byte[] code = new byte[codeEnd];
        System.arraycopy(bytes, 0, code, 0, codeEnd);
        return new MetadataStripped(code, true);
    }

    private String normalize(String hex) {
        String trimmed = hex.trim();
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
            trimmed = trimmed.substring(2);
        }
        if (trimmed.isEmpty()) {
            throw new ParseException(ParseException.BYTECODE_PARSE_ERROR, "Bytecode is empty");
        }
        if (!trimmed.matches("[0-9a-fA-F]+")) {
            throw new ParseException(ParseException.BYTECODE_PARSE_ERROR, "Bytecode contains non-hex characters");
        }
        if (trimmed.length() % 2 != 0) {
            throw new ParseException(ParseException.BYTECODE_PARSE_ERROR, "Bytecode has an odd number of hex digits");
        }
        return trimmed.toLowerCase();
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex digit at position " + i);
            }
            data[i / 2] = (byte) ((high << 4) | low);
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        return bytesToHex(bytes, 0, bytes.length);
    }

    private static String bytesToHex(byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = offset; i < offset + length; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xff));
        }
        return sb.toString();
    }

    private record MetadataStripped(byte[] code, boolean metadataPresent) {
    }
}
