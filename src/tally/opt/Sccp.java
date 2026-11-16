package tally.opt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tally.ir.Block;
import tally.ir.Function;
import tally.ir.Insn;

/**
 * Sparse conditional constant propagation (Wegman and Zadeck) on SSA form. Each vreg is TOP (no
 * value seen yet), a constant, or BOTTOM (varies); a block runs only if an executable edge reaches
 * it, and a phi meets only the arguments of executable edges. Afterwards every pure definition of a
 * constant becomes CONST, a branch on a constant becomes a jump, and blocks that never run go.
 * Integer semantics are the interpreter's: wrapping, division truncating toward zero. A division or
 * remainder by 0 is BOTTOM, so it still traps at run time.
 */
public final class Sccp {
    private static final int TOP = 0;
    private static final int CONST = 1;
    private static final int BOTTOM = 2;

    private final Function f;
    private final int[] state;
    private final int[] value;
    private final Map<Integer, List<Insn>> users = new HashMap<>();
    private final Map<Insn, Block> blockOf = new HashMap<>();
    private final Set<Block> liveBlocks = new HashSet<>();
    private final Set<String> liveEdges = new HashSet<>();
    private final Deque<Block[]> flow = new ArrayDeque<>();
    private final Deque<Insn> ssa = new ArrayDeque<>();

    private Sccp(Function f) {
        this.f = f;
        this.state = new int[f.vregCount()];
        this.value = new int[f.vregCount()];
    }

    public static boolean run(Function f) {
        return new Sccp(f).solve();
    }

    private static String edge(Block from, Block to) {
        return (from == null ? "-" : String.valueOf(from.id)) + ">" + to.id;
    }

    private boolean solve() {
        for (Block b : f.blocks) {
            for (Insn i : b.insns) {
                blockOf.put(i, b);
                for (int u : i.uses()) users.computeIfAbsent(u, k -> new ArrayList<>()).add(i);
            }
        }
        for (int p = 0; p < f.params; p++) state[p] = BOTTOM;
        flow.add(new Block[] {null, f.entry()});
        while (!flow.isEmpty() || !ssa.isEmpty()) {
            while (!flow.isEmpty()) {
                Block[] e = flow.poll();
                if (!liveEdges.add(edge(e[0], e[1]))) continue;
                Block b = e[1];
                boolean first = liveBlocks.add(b);
                for (Insn i : b.insns) {
                    if (i.op == Insn.Op.PHI) {
                        visit(i);
                    } else if (first) {
                        visit(i);
                    }
                }
            }
            while (!ssa.isEmpty()) {
                Insn i = ssa.poll();
                if (liveBlocks.contains(blockOf.get(i))) visit(i);
            }
        }
        return rewrite();
    }

    private void lower(int v, int st, int val) {
        if (st < state[v] || (st == state[v] && (st != CONST || val == value[v]))) return;
        if (state[v] == CONST && st == CONST && val != value[v]) st = BOTTOM;
        state[v] = st;
        value[v] = val;
        for (Insn u : users.getOrDefault(v, List.of())) ssa.add(u);
    }

    private void visit(Insn i) {
        Block b = blockOf.get(i);
        switch (i.op) {
            case CONST -> lower(i.dst, CONST, i.imm);
            case MOV -> lower(i.dst, state[i.a], value[i.a]);
            case NEG, NOT -> {
                int s = state[i.a];
                if (s == CONST) {
                    int v = value[i.a];
                    lower(i.dst, CONST, i.op == Insn.Op.NEG ? -v : (v == 0 ? 1 : 0));
                } else {
                    lower(i.dst, s, 0);
                }
            }
            case BIN -> {
                int sa = state[i.a];
                int sb = state[i.b];
                if (sa == CONST && sb == CONST) {
                    Integer v = evaluate(i.bop, value[i.a], value[i.b]);
                    if (v == null) lower(i.dst, BOTTOM, 0);
                    else lower(i.dst, CONST, v);
                } else if (sa == BOTTOM || sb == BOTTOM) {
                    lower(i.dst, BOTTOM, 0);
                }
            }
            case PHI -> {
                int st = TOP;
                int val = 0;
                for (int k = 0; k < i.args.length; k++) {
                    if (!liveEdges.contains(edge(i.from[k], b))) continue;
                    int a = i.args[k];
                    if (state[a] == TOP) continue;
                    if (state[a] == BOTTOM || (st == CONST && value[a] != val)) {
                        st = BOTTOM;
                        break;
                    }
                    st = CONST;
                    val = value[a];
                }
                if (st != TOP) lower(i.dst, st, val);
            }
            case BR -> {
                int s = state[i.a];
                if (s == CONST) {
                    flow.add(new Block[] {b, value[i.a] != 0 ? i.target : i.other});
                } else if (s == BOTTOM) {
                    flow.add(new Block[] {b, i.target});
                    flow.add(new Block[] {b, i.other});
                }
            }
            case JMP -> flow.add(new Block[] {b, i.target});
            default -> {
                int d = i.def();
                if (d >= 0) lower(d, BOTTOM, 0);
            }
        }
    }

    private boolean rewrite() {
        boolean changed = false;
        for (Block b : f.blocks) {
            if (!liveBlocks.contains(b)) continue;
            for (Insn i : b.insns) {
                int d = i.def();
                if (d >= 0 && state[d] == CONST && i.op != Insn.Op.CONST && i.isPure()) {
                    i.turnInto(Insn.constant(d, value[d]));
                    changed = true;
                } else if (i.op == Insn.Op.BR && state[i.a] == CONST) {
                    i.turnInto(Insn.jmp(value[i.a] != 0 ? i.target : i.other));
                    changed = true;
                }
            }
        }
        changed |= f.blocks.removeIf(b -> !liveBlocks.contains(b));
        Cleanup.fixPhis(f);
        return changed;
    }

    /** The value of a binary operation on constants, or null when it must be left to run time. */
    static Integer evaluate(Insn.BinOp op, int l, int r) {
        return switch (op) {
            case ADD -> l + r;
            case SUB -> l - r;
            case MUL -> l * r;
            case DIV -> r == 0 ? null : l / r;
            case REM -> r == 0 ? null : l % r;
            case LT -> l < r ? 1 : 0;
            case LE -> l <= r ? 1 : 0;
            case GT -> l > r ? 1 : 0;
            case GE -> l >= r ? 1 : 0;
            case EQ -> l == r ? 1 : 0;
            case NE -> l != r ? 1 : 0;
        };
    }
}
