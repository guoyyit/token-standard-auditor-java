package com.github.guoyyit.tokenstandardauditorjava.util;

import java.nio.charset.StandardCharsets;

/**
 * Minimal Keccak-256 (Ethereum variant, padding suffix 0x01) implementation.
 */
public final class Keccak256 {

    private static final int RATE_BYTES = 136; // 1088-bit rate for 256-bit output

    private static final long[] ROUND_CONSTANTS = {
            0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL, 0x8000000080008000L,
            0x000000000000808BL, 0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L,
            0x000000000000008AL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
            0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L, 0x8000000000008003L,
            0x8000000000008002L, 0x8000000000000080L, 0x000000000000800AL, 0x800000008000000AL,
            0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
    };

    private static final int[][] ROTATION = {
            {0, 36, 3, 41, 18},
            {1, 44, 10, 45, 2},
            {62, 6, 43, 15, 61},
            {28, 55, 25, 21, 56},
            {27, 20, 39, 8, 14}
    };

    private Keccak256() {
    }

    public static byte[] digest(byte[] input) {
        long[] state = new long[25];
        int offset = 0;
        while (offset + RATE_BYTES <= input.length) {
            absorbBlock(state, input, offset, RATE_BYTES);
            keccakF(state);
            offset += RATE_BYTES;
        }
        int remaining = input.length - offset;
        byte[] last = new byte[RATE_BYTES];
        System.arraycopy(input, offset, last, 0, remaining);
        last[remaining] |= 0x01;
        last[RATE_BYTES - 1] |= 0x80;
        absorbBlock(state, last, 0, RATE_BYTES);
        keccakF(state);

        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            out[i] = (byte) (state[i / 8] >>> (8 * (i % 8)));
        }
        return out;
    }

    public static byte[] digest(String text) {
        return digest(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String hexDigest(String text) {
        byte[] hash = digest(text);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    /** 4-byte function selector of a canonical signature, as 8 hex chars. */
    public static String functionSelector(String canonicalSignature) {
        return hexDigest(canonicalSignature).substring(0, 8);
    }

    private static void absorbBlock(long[] state, byte[] block, int offset, int length) {
        for (int i = 0; i < length; i++) {
            state[i / 8] ^= ((long) block[offset + i] & 0xff) << (8 * (i % 8));
        }
    }

    private static void keccakF(long[] a) {
        long[] c = new long[5];
        long[] b = new long[25];
        for (int round = 0; round < 24; round++) {
            for (int x = 0; x < 5; x++) {
                c[x] = a[x] ^ a[x + 5] ^ a[x + 10] ^ a[x + 15] ^ a[x + 20];
            }
            for (int x = 0; x < 5; x++) {
                long d = c[(x + 4) % 5] ^ Long.rotateLeft(c[(x + 1) % 5], 1);
                for (int y = 0; y < 5; y++) {
                    a[x + 5 * y] ^= d;
                }
            }
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    b[y + 5 * ((2 * x + 3 * y) % 5)] = Long.rotateLeft(a[x + 5 * y], ROTATION[x][y]);
                }
            }
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    a[x + 5 * y] = b[x + 5 * y] ^ ((~b[(x + 1) % 5 + 5 * y]) & b[(x + 2) % 5 + 5 * y]);
                }
            }
            a[0] ^= ROUND_CONSTANTS[round];
        }
    }
}
