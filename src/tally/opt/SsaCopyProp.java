package tally.opt;

import java.util.HashMap;
import java.util.Map;

import tally.ir.Block;
import tally.ir.Function;
import tally.ir.Insn;

/**
 * Global copy propagation on SSA form. Every vreg has 1 definition, so after {@code d = MOV s}
 * every read of d can read s instead, anywhere in the function. A phi whose arguments are all the
 * same vreg (or the phi itself) is a copy too. The copies are then deleted.
 */
public final class SsaCopyProp {
    private SsaCopyProp() {}

    public static boolean run(Function f) {
        Map<Integer, Integer> alias = new HashMap<>();
        boolean found = true;
        while (found) {
            found = false;
            for (Block b : f.blocks) {
                for (Insn i : b.insns) {
                    if (alias.containsKey(i.dst)) continue;
                    if (i.op == Insn.Op.MOV) {
                        alias.put(i.dst, resolve(alias, i.a));
                        found = true;
                    } else if (i.op == Insn.Op.PHI) {
                        int same = -1;
                        boolean copy = true;
                        for (int a : i.args) {
                            int r = resolve(alias, a);
                            if (r == i.dst) continue;
                            if (same == -1) same = r;
                            else if (same != r) copy = false;
                        }
                        if (copy && same >= 0) {
                            alias.put(i.dst, same);
                            found = true;
                        }
                    }
                }
            }
        }
        if (alias.isEmpty()) return false;
        for (Block b : f.blocks) {
            b.insns.removeIf(i -> (i.op == Insn.Op.MOV || i.op == Insn.Op.PHI) && alias.containsKey(i.dst));
            for (Insn i : b.insns) {
                for (int u : i.uses()) {
                    int r = resolve(alias, u);
                    if (r != u) i.replaceUse(u, r);
                }
            }
        }
        return true;
    }

    private static int resolve(Map<Integer, Integer> alias, int v) {
        Integer a = alias.get(v);
        while (a != null) {
            v = a;
            a = alias.get(v);
        }
        return v;
    }
}
