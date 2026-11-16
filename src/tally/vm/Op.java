package tally.vm;

import java.util.HashMap;
import java.util.Map;

/**
 * TVM opcodes. {@code operands} spells the operand kinds in order: r = register, i = integer,
 * m = memory operand [fp+k] / [fp-k], l = label. {@code cycles} is the cost of 1 execution.
 */
public enum Op {
    LI("li", "ri", 1),
    MOV("mov", "rr", 1),
    ADD("add", "rrr", 1),
    SUB("sub", "rrr", 1),
    MUL("mul", "rrr", 3),
    DIV("div", "rrr", 8),
    REM("rem", "rrr", 8),
    SLT("slt", "rrr", 1),
    SLE("sle", "rrr", 1),
    SGT("sgt", "rrr", 1),
    SGE("sge", "rrr", 1),
    SEQ("seq", "rrr", 1),
    SNE("sne", "rrr", 1),
    NEG("neg", "rr", 1),
    NOT("not", "rr", 1),
    LD("ld", "rm", 2),
    ST("st", "rm", 2),
    PUSH("push", "r", 2),
    POP("pop", "r", 2),
    NEWARR("newarr", "rr", 10),
    ALOAD("aload", "rrr", 3),
    ASTORE("astore", "rrr", 3),
    ALEN("alen", "rr", 1),
    READ("read", "r", 20),
    JMP("jmp", "l", 1),
    BZ("bz", "rl", 1),
    BNZ("bnz", "rl", 1),
    CALL("call", "l", 4),
    RET("ret", "", 4),
    ENTER("enter", "i", 2),
    LEAVE("leave", "", 2),
    ADDSP("addsp", "i", 1),
    PRINT("print", "r", 20),
    HALT("halt", "", 1);

    public final String mnemonic;
    public final String operands;
    public final int cycles;

    Op(String mnemonic, String operands, int cycles) {
        this.mnemonic = mnemonic;
        this.operands = operands;
        this.cycles = cycles;
    }

    private static final Map<String, Op> BY_NAME = new HashMap<>();

    static {
        for (Op o : values()) BY_NAME.put(o.mnemonic, o);
    }

    public static Op byName(String s) {
        return BY_NAME.get(s);
    }
}
