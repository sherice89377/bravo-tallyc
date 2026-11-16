package tally.opt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tally.ir.Block;
import tally.ir.Function;
import tally.ir.Insn;

/**
 * Loop inversion: a while loop tests its condition in the header and jumps back to it from the end
 * of the body. When the header is small, the end of the body gets its own copy of the test instead,
 * branching straight to the body or out of the loop, which saves 1 jump per iteration. The header
 * still runs the test for the first iteration. Runs before SSA construction, so the copies write the
 * same vregs as the original test.
 */
public final class LoopInversion {
    public static final int MAX_HEADER = 12;

    private LoopInversion() {}

    public static boolean run(Function f) {
        f.computePreds();
        List<Block> rpo = f.reversePostorder();
        Map<Block, Integer> order = new HashMap<>();
        for (int k = 0; k < rpo.size(); k++) order.put(rpo.get(k), k);
        Dominators dom = new Dominators(f);
        boolean changed = false;
        for (Block h : rpo) {
            Insn t = h.terminator();
            if (t == null || t.op != Insn.Op.BR || h.insns.size() > MAX_HEADER) continue;
            List<Block> latches = new ArrayList<>();
            for (Block p : h.preds) {
                if (dom.dominates(h, p) && !latches.contains(p)) latches.add(p);
            }
            if (latches.size() != 1) continue;
            Block latch = latches.get(0);
            Insn lt = latch.terminator();
            if (lt.op != Insn.Op.JMP || latch == h) continue;
            if (t.target == h || t.other == h) continue;
            latch.insns.remove(latch.insns.size() - 1);
            for (Insn i : h.insns) {
                latch.insns.add(i.copy());
            }
            changed = true;
        }
        if (changed) f.computePreds();
        return changed;
    }
}
