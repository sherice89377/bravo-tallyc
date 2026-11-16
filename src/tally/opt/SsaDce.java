package tally.opt;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tally.ir.Block;
import tally.ir.Function;
import tally.ir.Insn;

/**
 * Dead-code elimination on SSA form, mark and sweep: every instruction with an effect (or that may
 * trap) is useful, and so is the definition of every vreg a useful instruction reads. Pure
 * instructions that are not useful go.
 */
public final class SsaDce {
    private SsaDce() {}

    public static boolean run(Function f) {
        Map<Integer, Insn> def = new HashMap<>();
        for (Block b : f.blocks) {
            for (Insn i : b.insns) {
                if (i.def() >= 0) def.put(i.def(), i);
            }
        }
        Set<Insn> useful = new HashSet<>();
        Deque<Insn> work = new ArrayDeque<>();
        for (Block b : f.blocks) {
            for (Insn i : b.insns) {
                if (!i.isPure() || i.isTerminator()) {
                    useful.add(i);
                    work.add(i);
                }
            }
        }
        while (!work.isEmpty()) {
            Insn i = work.poll();
            for (int u : i.uses()) {
                Insn d = def.get(u);
                if (d != null && useful.add(d)) work.add(d);
            }
        }
        boolean changed = false;
        for (Block b : f.blocks) {
            changed |= b.insns.removeIf(i -> i.isPure() && !i.isTerminator() && !useful.contains(i));
        }
        return changed;
    }
}
