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
 * Construction of pruned SSA form (Cytron et al.): a phi for vreg v goes at every block in the
 * iterated dominance frontier of v's definitions where v is live on entry; then every definition
 * gets a new vreg, renamed along the dominator tree. Parameters keep their numbers: vreg p < params
 * is still parameter p on entry.
 */
public final class Ssa {
    private final Function f;
    private final Dominators dom;
    private final Map<Integer, Deque<Integer>> stacks = new HashMap<>();
    private final Map<Insn, Integer> phiVar = new HashMap<>();

    private Ssa(Function f) {
        this.f = f;
        this.dom = new Dominators(f);
    }

    public static void build(Function f) {
        new Ssa(f).run();
    }

    private void run() {
        Liveness live = Liveness.compute(f);
        Map<Integer, Set<Block>> defs = new HashMap<>();
        for (Block b : dom.rpo) {
            for (Insn i : b.insns) {
                int d = i.def();
                if (d >= 0) defs.computeIfAbsent(d, k -> new HashSet<>()).add(b);
            }
        }
        for (int p = 0; p < f.params; p++) {
            defs.computeIfAbsent(p, k -> new HashSet<>()).add(f.entry());
        }
        for (Map.Entry<Integer, Set<Block>> e : defs.entrySet()) {
            int v = e.getKey();
            Set<Block> placed = new HashSet<>();
            Deque<Block> work = new ArrayDeque<>(e.getValue());
            Set<Block> queued = new HashSet<>(e.getValue());
            while (!work.isEmpty()) {
                Block b = work.pop();
                for (Block y : dom.frontier(b)) {
                    if (placed.contains(y) || !live.in[y.id].get(v)) continue;
                    placed.add(y);
                    List<Block> preds = distinctPreds(y);
                    Insn phi = Insn.phi(v, new int[preds.size()], preds.toArray(new Block[0]));
                    java.util.Arrays.fill(phi.args, v);
                    y.insns.add(0, phi);
                    phiVar.put(phi, v);
                    if (queued.add(y)) work.push(y);
                }
            }
        }
        for (int p = 0; p < f.params; p++) push(p, p);
        rename(f.entry());
    }

    private static List<Block> distinctPreds(Block b) {
        List<Block> out = new ArrayList<>();
        for (Block p : b.preds) {
            if (!out.contains(p)) out.add(p);
        }
        return out;
    }

    private void push(int v, int name) {
        stacks.computeIfAbsent(v, k -> new ArrayDeque<>()).push(name);
    }

    private int top(int v) {
        Deque<Integer> s = stacks.get(v);
        if (s == null || s.isEmpty()) {
            throw new IllegalStateException("v" + v + " is read before any definition reaches it");
        }
        return s.peek();
    }

    private void rename(Block b) {
        List<Integer> pushed = new ArrayList<>();
        for (Insn i : b.insns) {
            if (i.op != Insn.Op.PHI) {
                for (int u : i.uses()) {
                    i.replaceUse(u, top(u));
                }
            }
            int d = i.def();
            if (d >= 0) {
                int fresh = f.newVreg();
                i.dst = fresh;
                push(d, fresh);
                pushed.add(d);
            }
        }
        for (Block s : b.succs()) {
            for (Insn phi : s.insns) {
                if (phi.op != Insn.Op.PHI) break;
                int v = phiVar.get(phi);
                for (int k = 0; k < phi.from.length; k++) {
                    if (phi.from[k] == b) phi.args[k] = top(v);
                }
            }
        }
        for (Block c : dom.children(b)) {
            rename(c);
        }
        for (int k = pushed.size() - 1; k >= 0; k--) {
            stacks.get(pushed.get(k)).pop();
        }
    }
}
