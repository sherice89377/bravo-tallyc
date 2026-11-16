package tally.opt;

import java.util.List;

import tally.ir.Block;
import tally.ir.Function;
import tally.ir.Insn;

/**
 * Live-variable analysis over the blocks reachable from the entry. A vreg is live at a point when
 * some path from that point reads it before writing it. Results are indexed by block id:
 * {@code in[id]} holds the vregs live on entry to the block, {@code out[id]} those live on exit.
 */
public final class Liveness {
    public final LiveSet[] in;
    public final LiveSet[] out;

    private Liveness(int blocks) {
        in = new LiveSet[blocks];
        out = new LiveSet[blocks];
    }

    public static Liveness compute(Function f) {
        int n = 0;
        for (Block b : f.blocks) n = Math.max(n, b.id + 1);
        Liveness live = new Liveness(n);
        LiveSet[] use = new LiveSet[n];
        LiveSet[] def = new LiveSet[n];
        List<Block> rpo = f.reversePostorder();
        for (Block b : f.blocks) {
            live.in[b.id] = new LiveSet();
            live.out[b.id] = new LiveSet();
            use[b.id] = new LiveSet();
            def[b.id] = new LiveSet();
            for (Insn i : b.insns) {
                for (int u : i.uses()) {
                    if (!def[b.id].get(u)) use[b.id].set(u);
                }
                int d = i.def();
                if (d >= 0) def[b.id].set(d);
            }
        }
        // Backward problem: visit blocks in postorder so successors are mostly done first, and repeat
        // until nothing changes (a loop's back edge needs at least 1 more pass).
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int k = rpo.size() - 1; k >= 0; k--) {
                Block b = rpo.get(k);
                LiveSet out = live.out[b.id];
                for (Block s : b.succs()) {
                    changed |= out.addAll(live.in[s.id]);
                }
                LiveSet in = out.copy();
                in.removeAll(def[b.id]);
                in.addAll(use[b.id]);
                changed |= live.in[b.id].addAll(in);
            }
        }
        return live;
    }
}
