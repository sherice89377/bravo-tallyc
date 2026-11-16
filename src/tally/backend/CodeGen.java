package tally.backend;

import java.util.List;

import tally.ir.Block;
import tally.ir.Function;
import tally.ir.Insn;

/**
 * Emits TVM assembly (docs/TVM.md) for 1 allocated function.
 *
 * Frame: {@code enter N} reserves the N spill slots, then the prologue pushes the saved registers in
 * ascending order and loads each parameter that lives in a register from its argument slot. Every
 * return moves its value to r0, pops the saved registers in descending order and leaves.
 * A caller pushes arguments last to first, calls, then drops them with {@code addsp}.
 *
 * Operands in memory are loaded into scratch registers: r7 for the 1st, r0 for the 2nd. A result in
 * memory is computed in r7 and stored.
 */
public final class CodeGen {
    private static final int S1 = 7;
    private static final int S2 = 0;

    private final Function fn;
    private final LinearScan alloc;
    private final StringBuilder out;

    private CodeGen(Function fn, LinearScan alloc, StringBuilder out) {
        this.fn = fn;
        this.alloc = alloc;
        this.out = out;
    }

    public static void emit(Function fn, LinearScan alloc, StringBuilder out) {
        new CodeGen(fn, alloc, out).function();
    }

    private void function() {
        List<Block> layout = alloc.intervals.layout;
        out.append(fn.name).append(":\n");
        line("enter " + alloc.spillSlots);
        for (int r : alloc.savedRegs) {
            line("push r" + r);
        }
        for (int p = 0; p < fn.params; p++) {
            Location l = alloc.where.get(p);
            if (l != null && l.inRegister()) {
                line("ld r" + l.index() + ", " + Location.arg(p).memory());
            }
        }
        for (int k = 0; k < layout.size(); k++) {
            Block b = layout.get(k);
            Block next = k + 1 < layout.size() ? layout.get(k + 1) : null;
            out.append(label(b)).append(":\n");
            for (Insn i : b.insns) {
                insn(i, next);
            }
        }
    }

    private String label(Block b) {
        return fn.name + ".B" + b.id;
    }

    private void line(String s) {
        out.append("    ").append(s).append('\n');
    }

    /** The register holding vreg v, loading it into {@code scratch} first when it lives in memory. */
    private int use(int v, int scratch) {
        Location l = alloc.locate(v);
        if (l.inRegister()) return l.index();
        line("ld r" + scratch + ", " + l.memory());
        return scratch;
    }

    /** The register to compute vreg v into: its own, or r7 when it lives in memory. */
    private int target(int v) {
        Location l = alloc.locate(v);
        return l.inRegister() ? l.index() : S1;
    }

    /** Stores r7 to v's memory location when v lives in memory. */
    private void writeBack(int v) {
        Location l = alloc.locate(v);
        if (!l.inRegister()) line("st r" + S1 + ", " + l.memory());
    }

    private void insn(Insn i, Block next) {
        switch (i.op) {
            case CONST -> {
                int d = target(i.dst);
                line("li r" + d + ", " + i.imm);
                writeBack(i.dst);
            }
            case MOV -> {
                Location src = alloc.locate(i.a);
                Location dst = alloc.locate(i.dst);
                if (src.equals(dst)) return;
                int s = use(i.a, S1);
                if (dst.inRegister()) {
                    line("mov r" + dst.index() + ", r" + s);
                } else {
                    line("st r" + s + ", " + dst.memory());
                }
            }
            case BIN -> {
                int a = use(i.a, S1);
                int b = use(i.b, S2);
                int d = target(i.dst);
                line(binName(i.bop) + " r" + d + ", r" + a + ", r" + b);
                writeBack(i.dst);
            }
            case NEG, NOT, NEWARR, ALEN -> {
                int a = use(i.a, S1);
                int d = target(i.dst);
                String name = switch (i.op) {
                    case NEG -> "neg";
                    case NOT -> "not";
                    case NEWARR -> "newarr";
                    default -> "alen";
                };
                line(name + " r" + d + ", r" + a);
                writeBack(i.dst);
            }
            case ALOAD -> {
                int a = use(i.a, S1);
                int b = use(i.b, S2);
                int d = target(i.dst);
                line("aload r" + d + ", r" + a + ", r" + b);
                writeBack(i.dst);
            }
            case ASTORE -> astore(i);
            case PHI -> throw new IllegalStateException("a phi reached code generation");
            case READ -> {
                int d = target(i.dst);
                line("read r" + d);
                writeBack(i.dst);
            }
            case CALL -> {
                for (int k = i.args.length - 1; k >= 0; k--) {
                    line("push r" + use(i.args[k], S1));
                }
                line("call " + i.callee);
                if (i.args.length > 0) line("addsp " + i.args.length);
                if (i.dst >= 0) {
                    Location d = alloc.locate(i.dst);
                    if (d.inRegister()) {
                        line("mov r" + d.index() + ", r0");
                    } else {
                        line("st r0, " + d.memory());
                    }
                }
            }
            case PRINT -> line("print r" + use(i.a, S1));
            case RET -> {
                if (i.a >= 0) {
                    Location l = alloc.locate(i.a);
                    if (l.inRegister()) {
                        line("mov r0, r" + l.index());
                    } else {
                        line("ld r0, " + l.memory());
                    }
                }
                for (int k = alloc.savedRegs.size() - 1; k >= 0; k--) {
                    line("pop r" + alloc.savedRegs.get(k));
                }
                line("leave");
                line("ret");
            }
            case JMP -> {
                if (i.target != next) line("jmp " + label(i.target));
            }
            case BR -> {
                int c = use(i.a, S1);
                if (i.target == next) {
                    line("bz r" + c + ", " + label(i.other));
                } else {
                    line("bnz r" + c + ", " + label(i.target));
                    if (i.other != next) line("jmp " + label(i.other));
                }
            }
        }
    }

    /**
     * {@code a[b] = c}. The value takes whichever scratch register the array and the index left
     * free. Only 2 scratch registers exist, so when all 3 operands live in memory the value goes
     * through a register borrowed for the instruction: pushed, loaded, used, popped.
     */
    private void astore(Insn i) {
        int a = use(i.a, S1);
        int b = use(i.b, S2);
        Location vl = alloc.locate(i.c);
        if (vl.inRegister()) {
            line("astore r" + a + ", r" + b + ", r" + vl.index());
            return;
        }
        int scratch = a != S1 ? S1 : (b != S2 ? S2 : -1);
        if (scratch >= 0) {
            line("ld r" + scratch + ", " + vl.memory());
            line("astore r" + a + ", r" + b + ", r" + scratch);
            return;
        }
        int borrow = 1;
        while (borrow == a || borrow == b) borrow++;
        line("push r" + borrow);
        line("ld r" + borrow + ", " + vl.memory());
        line("astore r" + a + ", r" + b + ", r" + borrow);
        line("pop r" + borrow);
    }

    private static String binName(Insn.BinOp op) {
        return switch (op) {
            case ADD -> "add";
            case SUB -> "sub";
            case MUL -> "mul";
            case DIV -> "div";
            case REM -> "rem";
            case LT -> "slt";
            case LE -> "sle";
            case GT -> "sgt";
            case GE -> "sge";
            case EQ -> "seq";
            case NE -> "sne";
        };
    }
}
