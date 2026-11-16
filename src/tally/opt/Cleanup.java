package tally.opt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tally.ir.Block;
import tally.ir.Function;
import tally.ir.Insn;

/**
 * CFG tidying: a branch whose 2 targets are the same becomes a jump, unreachable blocks go, and in
 * SSA form every phi keeps exactly the arguments of edges that still exist. A phi left with 1
 * argument becomes a move.
 */
public final class Cleanup {
    private Cleanup() {}

    public static boolean run(Function f) {
        boolean changed = false;
        for (Block b : f.blocks) {
            Insn t = b.terminator();
            if (t != null && t.op == Insn.Op.BR && t.target == t.other) {
                t.turnInto(Insn.jmp(t.target));
                changed = true;
            }
        }
        Set<Block> reachable = new HashSet<>(f.reversePostorder());
        if (f.blocks.removeIf(b -> !reachable.contains(b))) {
            changed = true;
        }
        fixPhis(f);
        return changed;
    }

    /** Recomputes predecessors and drops phi arguments whose edge is gone. */
    public static void fixPhis(Function f) {
        f.computePreds();
        for (Block b : f.blocks) {
            for (Insn i : b.insns) {
                if (i.op != Insn.Op.PHI) continue;
                List<Integer> args = new ArrayList<>();
                List<Block> from = new ArrayList<>();
                for (int k = 0; k < i.args.length; k++) {
                    if (b.preds.contains(i.from[k]) && !from.contains(i.from[k])) {
                        args.add(i.args[k]);
                        from.add(i.from[k]);
                    }
                }
                if (args.size() == 1) {
                    i.turnInto(Insn.mov(i.dst, args.get(0)));
                } else {
                    i.args = args.stream().mapToInt(Integer::intValue).toArray();
                    i.from = from.toArray(new Block[0]);
                }
            }
        }
    }
}
