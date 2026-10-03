package com.github.guoyyit.tokenstandardauditorjava.parse;

import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.nio.charset.StandardCharsets;

/**
 * Keccak-256 hashing utilities used for EVM function selectors and event topics.
 */
public final class Keccak256 {

    private Keccak256() {
    }

    /**
     * Returns the Keccak-256 hash of the given input bytes.
     */
    public static byte[] hash(byte[] input) {
        Keccak.Digest256 digest = new Keccak.Digest256();
        return digest.digest(input);
    }

    /**
     * Returns the Keccak-256 hash of the UTF-8 encoded input string.
     */
    public static byte[] hash(String input) {
        return hash(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the first 4 bytes of the Keccak-256 hash, as a 0x-prefixed hex string.
     */
    public static String selector(String signature) {
        byte[] hash = hash(signature);
        return toHex(hash, 0, 4);
    }

    /**
     * Returns the full 32-byte Keccak-256 hash, as a 0x-prefixed hex string.
     */
    public static String topic(String signature) {
        byte[] hash = hash(signature);
        return toHex(hash, 0, 32);
    }

    private static String toHex(byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder(2 + length * 2);
        sb.append("0x");
        for (int i = offset; i < offset + length; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xff));
        }
        return sb.toString();
    }
}
