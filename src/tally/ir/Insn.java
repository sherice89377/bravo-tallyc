package tally.ir;

import java.util.Arrays;

/**
 * 1 3-address instruction on virtual registers (vregs). Not SSA: a vreg may be assigned many
 * times (every source variable is 1 vreg). Fields that an opcode does not use are -1 or null.
 *
 * <pre>
 *   CONST  dst = imm
 *   MOV    dst = a
 *   BIN    dst = a (bop) b          DIV and REM trap on a zero divisor
 *   NEG    dst = -a
 *   NOT    dst = (a == 0)
 *   NEWARR dst = new int[a]        traps on a negative or too large size
 *   ALOAD  dst = a[b]              traps out of bounds
 *   ASTORE a[b] = c                traps out of bounds
 *   ALEN   dst = len(a)
 *   READ   dst = the next input integer (0 when the input is used up)
 *   CALL   dst = callee(args...)   dst is -1 for a call whose value is unused or absent
 *   PRINT  print a
 *   RET    return a                a is -1 in a function that returns nothing
 *   PHI    dst = the arg whose block in from[] the control came from (SSA form only)
 *   JMP    goto target
 *   BR     if a != 0 goto target else goto other
 * </pre>
 */
public final class Insn {
    public enum Op { CONST, MOV, BIN, NEG, NOT, NEWARR, ALOAD, ASTORE, ALEN, READ, CALL, PRINT, RET, JMP, BR, PHI }

    public enum BinOp { ADD, SUB, MUL, DIV, REM, LT, LE, GT, GE, EQ, NE }

    public Op op;
    public int dst = -1;
    public int a = -1;
    public int b = -1;
    public int c = -1;
    public int imm;
    public BinOp bop;
    public String callee;
    public int[] args;
    public Block target;
    public Block other;
    /** For PHI: the predecessor each of {@code args} comes from. */
    public Block[] from;

    private Insn(Op op) {
        this.op = op;
    }

    public static Insn constant(int dst, int imm) {
        Insn i = new Insn(Op.CONST);
        i.dst = dst;
        i.imm = imm;
        return i;
    }

    public static Insn mov(int dst, int src) {
        Insn i = new Insn(Op.MOV);
        i.dst = dst;
        i.a = src;
        return i;
    }

    public static Insn bin(BinOp bop, int dst, int a, int b) {
        Insn i = new Insn(Op.BIN);
        i.bop = bop;
        i.dst = dst;
        i.a = a;
        i.b = b;
        return i;
    }

    public static Insn unary(Op op, int dst, int a) {
        Insn i = new Insn(op);
        i.dst = dst;
        i.a = a;
        return i;
    }

    public static Insn newArray(int dst, int size) {
        return unary(Op.NEWARR, dst, size);
    }

    public static Insn aload(int dst, int arr, int idx) {
        Insn i = new Insn(Op.ALOAD);
        i.dst = dst;
        i.a = arr;
        i.b = idx;
        return i;
    }

    public static Insn astore(int arr, int idx, int val) {
        Insn i = new Insn(Op.ASTORE);
        i.a = arr;
        i.b = idx;
        i.c = val;
        return i;
    }

    public static Insn alen(int dst, int arr) {
        return unary(Op.ALEN, dst, arr);
    }

    public static Insn read(int dst) {
        Insn i = new Insn(Op.READ);
        i.dst = dst;
        return i;
    }

    public static Insn call(int dst, String callee, int[] args) {
        Insn i = new Insn(Op.CALL);
        i.dst = dst;
        i.callee = callee;
        i.args = args;
        return i;
    }

    public static Insn print(int a) {
        Insn i = new Insn(Op.PRINT);
        i.a = a;
        return i;
    }

    public static Insn ret(int a) {
        Insn i = new Insn(Op.RET);
        i.a = a;
        return i;
    }

    public static Insn phi(int dst, int[] args, Block[] from) {
        Insn i = new Insn(Op.PHI);
        i.dst = dst;
        i.args = args;
        i.from = from;
        return i;
    }

    /** A copy of this instruction with its own operand arrays (blocks are shared). */
    public Insn copy() {
        Insn i = new Insn(op);
        i.turnInto(this);
        if (args != null) i.args = args.clone();
        if (from != null) i.from = from.clone();
        return i;
    }

    public static Insn jmp(Block target) {
        Insn i = new Insn(Op.JMP);
        i.target = target;
        return i;
    }

    public static Insn br(int cond, Block target, Block other) {
        Insn i = new Insn(Op.BR);
        i.a = cond;
        i.target = target;
        i.other = other;
        return i;
    }

    public boolean isTerminator() {
        return op == Op.RET || op == Op.JMP || op == Op.BR;
    }

    /** The vregs this instruction reads, in operand order. */
    public int[] uses() {
        return switch (op) {
            case CONST, JMP, READ -> new int[0];
            case MOV, NEG, NOT, NEWARR, ALEN, PRINT, BR -> new int[] {a};
            case BIN, ALOAD -> new int[] {a, b};
            case ASTORE -> new int[] {a, b, c};
            case CALL, PHI -> args.clone();
            case RET -> a < 0 ? new int[0] : new int[] {a};
        };
    }

    /** Replaces every read of vreg {@code from} with {@code to}. */
    public void replaceUse(int from, int to) {
        switch (op) {
            case CALL, PHI -> {
                for (int k = 0; k < args.length; k++) {
                    if (args[k] == from) args[k] = to;
                }
            }
            case CONST, JMP, READ -> { }
            default -> {
                if (a == from) a = to;
                if (b == from) b = to;
                if (c == from) c = to;
            }
        }
    }

    /** The vreg this instruction writes, or -1. */
    public int def() {
        return switch (op) {
            case CONST, MOV, BIN, NEG, NOT, NEWARR, ALOAD, ALEN, READ -> dst;
            case CALL, PHI -> dst;
            default -> -1;
        };
    }

    /** True when removing this instruction cannot change what the program does, if its result is unused. */
    public boolean isPure() {
        return switch (op) {
            case CONST, MOV, NEG, NOT, ALEN, PHI -> true;
            case BIN -> bop != BinOp.DIV && bop != BinOp.REM;
            default -> false;
        };
    }

    public void turnInto(Insn other) {
        this.op = other.op;
        this.dst = other.dst;
        this.a = other.a;
        this.b = other.b;
        this.c = other.c;
        this.imm = other.imm;
        this.bop = other.bop;
        this.callee = other.callee;
        this.args = other.args;
        this.target = other.target;
        this.other = other.other;
        this.from = other.from;
    }

    @Override
    public String toString() {
        return switch (op) {
            case CONST -> "v" + dst + " = " + imm;
            case MOV -> "v" + dst + " = v" + a;
            case BIN -> "v" + dst + " = " + bop.name().toLowerCase() + " v" + a + ", v" + b;
            case NEG -> "v" + dst + " = neg v" + a;
            case NOT -> "v" + dst + " = not v" + a;
            case NEWARR -> "v" + dst + " = newarr v" + a;
            case ALOAD -> "v" + dst + " = v" + a + "[v" + b + "]";
            case ASTORE -> "v" + a + "[v" + b + "] = v" + c;
            case ALEN -> "v" + dst + " = len v" + a;
            case READ -> "v" + dst + " = read";
            case CALL -> (dst >= 0 ? "v" + dst + " = " : "") + "call " + callee
                    + Arrays.toString(Arrays.stream(args).mapToObj(x -> "v" + x).toArray());
            case PRINT -> "print v" + a;
            case RET -> a < 0 ? "ret" : "ret v" + a;
            case JMP -> "jmp B" + target.id;
            case BR -> "br v" + a + ", B" + target.id + ", B" + other.id;
            case PHI -> {
                StringBuilder sb = new StringBuilder("v" + dst + " = phi");
                for (int k = 0; k < args.length; k++) sb.append(k == 0 ? " " : ", ").append("[B").append(from[k].id).append(": v").append(args[k]).append(']');
                yield sb.toString();
            }
        };
    }
}
