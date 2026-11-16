package tally.opt;

import java.util.List;

import tally.ir.Block;
import tally.ir.Function;
import tally.ir.Insn;

/**
 * Dead-code elimination: removes pure instructions whose result is never read, and drops the unused
 * result of a call (the call itself stays). Repeats until nothing more goes.
 */
public final class DeadCode {
    private DeadCode() {}

    public static boolean run(Function f) {
        boolean any = false;
        boolean changed = true;
        while (changed) {
            changed = false;
            Liveness live = Liveness.compute(f);
            for (Block b : f.reversePostorder()) {
                LiveSet alive = live.out[b.id].copy();
                List<Insn> insns = b.insns;
                for (int k = insns.size() - 1; k >= 0; k--) {
                    Insn i = insns.get(k);
                    int d = i.def();
                    if (d >= 0 && !alive.get(d)) {
                        if (i.isPure()) {
                            insns.remove(k);
                            changed = true;
                            continue;
                        }
                        if (i.op == Insn.Op.CALL) {
                            i.dst = -1;
                            changed = true;
                        }
                    }
                    if (d >= 0) alive.clear(d);
                    for (int u : i.uses()) alive.set(u);
                }
            }
            any |= changed;
        }
        return any;
    }
}
