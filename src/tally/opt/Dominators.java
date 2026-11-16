package tally.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tally.ir.Block;
import tally.ir.Function;

/**
 * Immediate dominators (Cooper, Harvey and Kennedy, "A Simple, Fast Dominance Algorithm"), the
 * dominator tree and dominance frontiers, over the blocks reachable from the entry.
 */
public final class Dominators {
    public final List<Block> rpo;
    private final Map<Block, Integer> order = new HashMap<>();
    private final Map<Block, Block> idom = new HashMap<>();
    private final Map<Block, List<Block>> children = new HashMap<>();
    private final Map<Block, Set<Block>> frontier = new HashMap<>();

    public Dominators(Function f) {
        f.computePreds();
        rpo = f.reversePostorder();
        for (int i = 0; i < rpo.size(); i++) order.put(rpo.get(i), i);
        Block entry = rpo.get(0);
        idom.put(entry, entry);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 1; i < rpo.size(); i++) {
                Block b = rpo.get(i);
                Block best = null;
                for (Block p : b.preds) {
                    if (!idom.containsKey(p)) continue;
                    best = best == null ? p : intersect(p, best);
                }
                if (best != null && idom.get(b) != best) {
                    idom.put(b, best);
                    changed = true;
                }
            }
        }
        for (Block b : rpo) {
            children.put(b, new ArrayList<>());
            frontier.put(b, new HashSet<>());
        }
        for (Block b : rpo) {
            if (b != entry) children.get(idom.get(b)).add(b);
        }
        for (Block b : rpo) {
            List<Block> preds = new ArrayList<>();
            for (Block p : b.preds) {
                if (order.containsKey(p) && !preds.contains(p)) preds.add(p);
            }
            if (preds.size() < 2) continue;
            for (Block p : preds) {
                Block runner = p;
                while (runner != idom.get(b)) {
                    frontier.get(runner).add(b);
                    runner = idom.get(runner);
                }
            }
        }
    }

    private Block intersect(Block a, Block b) {
        while (a != b) {
            while (order.get(a) > order.get(b)) a = idom.get(a);
            while (order.get(b) > order.get(a)) b = idom.get(b);
        }
        return a;
    }

    public Block idom(Block b) {
        return idom.get(b);
    }

    public List<Block> children(Block b) {
        return children.get(b);
    }

    public Set<Block> frontier(Block b) {
        return frontier.get(b);
    }

    public boolean dominates(Block a, Block b) {
        Block x = b;
        while (true) {
            if (x == a) return true;
            Block up = idom.get(x);
            if (up == null || up == x) return false;
            x = up;
        }
    }
}
