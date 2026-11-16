package tally.opt;

import java.util.ArrayList;
import java.util.List;

import tally.ir.Block;
import tally.ir.Function;
import tally.ir.Insn;

/**
 * Leaves SSA form. A phi at the top of block b says: coming from predecessor p, the destination
 * takes the argument for p. So each phi becomes a move at the end of each predecessor, just before
 * its terminator, and the phis are removed. Moves whose 2 vregs are the same are left out, and
 * coalescing later removes most of the others.
 */
public final class OutOfSsa {
    private OutOfSsa() {}

    public static void run(Function f) {
        f.computePreds();
        for (Block b : new ArrayList<>(f.blocks)) {
            List<Insn> phis = new ArrayList<>();
            for (Insn i : b.insns) {
                if (i.op == Insn.Op.PHI) phis.add(i);
            }
            if (phis.isEmpty()) continue;
            List<Block> preds = new ArrayList<>();
            for (Block p : b.preds) {
                if (!preds.contains(p)) preds.add(p);
            }
            for (Block p : preds) {
                Insn term = p.insns.remove(p.insns.size() - 1);
                for (Insn phi : phis) {
                    for (int k = 0; k < phi.from.length; k++) {
                        if (phi.from[k] == p && phi.dst != phi.args[k]) {
                            p.insns.add(Insn.mov(phi.dst, phi.args[k]));
                        }
                    }
                }
                p.insns.add(term);
            }
            b.insns.removeAll(phis);
        }
        f.computePreds();
    }
}
