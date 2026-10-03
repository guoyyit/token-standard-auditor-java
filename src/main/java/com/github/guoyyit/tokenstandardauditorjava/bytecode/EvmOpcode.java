package com.github.guoyyit.tokenstandardauditorjava.bytecode;

/**
 * One decoded EVM instruction: opcode byte plus absolute offset in the code.
 */
public record EvmOpcode(int offset, int opcode, byte[] pushData) {

    public boolean isPush4() {
        return opcode == 0x63;
    }

    public boolean isPush32() {
        return opcode == 0x7f;
    }

    public boolean isLog() {
        return opcode >= 0xa0 && opcode <= 0xa4;
    }

    public int logTopicCount() {
        return isLog() ? opcode - 0xa0 : -1;
    }
}
