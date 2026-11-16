package tally.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tally.ir.Block;
import tally.ir.Function;
import tally.ir.Insn;

/**
 * Copy coalescing after SSA form is left: a move {@code d = s} whose 2 vregs never hold different
 * live values at the same time is removed by renaming s to d everywhere (or d to a parameter when
 * the other is a parameter). 2 vregs interfere when 1 is defined while the other is live just after
 * that instruction; the source of a move does not interfere with its destination there. Merged
 * vregs take the union of their interferences, so later merges stay safe.
 */
public final class Coalesce {
    private Coalesce() {}

    public static boolean run(Function f) {
        Liveness live = Liveness.compute(f);
        Map<Integer, Set<Integer>> graph = new HashMap<>();
        List<Insn> moves = new ArrayList<>();
        for (Block b : f.reversePostorder()) {
            LiveSet alive = live.out[b.id].copy();
            for (int k = b.insns.size() - 1; k >= 0; k--) {
                Insn i = b.insns.get(k);
                int d = i.def();
                if (d >= 0) {
                    for (int v = alive.next(0); v >= 0; v = alive.next(v + 1)) {
                        if (v == d || (i.op == Insn.Op.MOV && v == i.a)) continue;
                        edge(graph, d, v);
                    }
                    alive.clear(d);
                }
                if (i.op == Insn.Op.MOV) moves.add(i);
                for (int u : i.uses()) alive.set(u);
            }
        }
        // Parameters are all live together on entry.
        for (int p = 0; p < f.params; p++) {
            for (int q = p + 1; q < f.params; q++) edge(graph, p, q);
        }
        Map<Integer, Integer> rename = new HashMap<>();
        boolean changed = false;
        for (Insn m : moves) {
            int d = find(rename, m.dst);
            int s = find(rename, m.a);
            if (d == s) continue;
            if (graph.getOrDefault(d, Set.of()).contains(s)) continue;
            if (d < f.params && s < f.params) continue;
            int keep = s < f.params ? s : d;
            int gone = keep == s ? d : s;
            rename.put(gone, keep);
            Set<Integer> into = graph.computeIfAbsent(keep, x -> new HashSet<>());
            for (int n : graph.getOrDefault(gone, Set.of())) {
                int r = find(rename, n);
                into.add(r);
                graph.computeIfAbsent(r, x -> new HashSet<>()).add(keep);
            }
            changed = true;
        }
        if (!changed) return false;
        for (Block b : f.blocks) {
            for (Insn i : b.insns) {
                for (int u : i.uses()) {
                    int r = find(rename, u);
                    if (r != u) i.replaceUse(u, r);
                }
                int d = i.def();
                if (d >= 0) i.dst = find(rename, d);
            }
            b.insns.removeIf(i -> i.op == Insn.Op.MOV && i.dst == i.a);
        }
        return true;
    }

    private static void edge(Map<Integer, Set<Integer>> g, int a, int b) {
        g.computeIfAbsent(a, x -> new HashSet<>()).add(b);
        g.computeIfAbsent(b, x -> new HashSet<>()).add(a);
    }

    private static int find(Map<Integer, Integer> rename, int v) {
        Integer r = rename.get(v);
        while (r != null) {
            v = r;
            r = rename.get(v);
        }
        return v;
    }
}
