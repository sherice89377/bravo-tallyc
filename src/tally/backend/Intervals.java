package tally.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tally.ir.Block;
import tally.ir.Function;
import tally.ir.Insn;
import tally.opt.LiveSet;
import tally.opt.Liveness;

/**
 * Live intervals for linear-scan allocation. Blocks are laid out in reverse postorder and the
 * instructions numbered from 0; instruction n reads its operands at position 2n and writes its
 * result at 2n+1, so a value read for the last time by an instruction may share a register with
 * that instruction's result. Each vreg gets 1 interval [start, end] with no holes: from its first
 * definition or live-in point to its last use or live-out point. Parameters start at -1.
 */
public final class Intervals {
    public static final class Interval {
        public final int vreg;
        public int start = Integer.MAX_VALUE;
        public int end = Integer.MIN_VALUE;

        Interval(int vreg) {
            this.vreg = vreg;
        }

        void cover(int pos) {
            start = Math.min(start, pos);
            end = Math.max(end, pos);
        }

        @Override
        public String toString() {
            return "v" + vreg + "[" + start + "," + end + "]";
        }
    }

    public final List<Block> layout;
    public final List<Interval> all = new ArrayList<>();
    private final Map<Integer, Interval> byVreg = new HashMap<>();

    private Intervals(List<Block> layout) {
        this.layout = layout;
    }

    public static Intervals build(Function f) {
        Liveness live = Liveness.compute(f);
        Intervals iv = new Intervals(f.reversePostorder());
        for (int p = 0; p < f.params; p++) {
            iv.of(p).cover(-1);
        }
        int n = 0;
        for (Block b : iv.layout) {
            int first = n;
            int last = n + b.insns.size() - 1;
            int blockStart = 2 * first;
            int blockEnd = 2 * last + 1;
            LiveSet in = live.in[b.id];
            for (int v = in.next(0); v >= 0; v = in.next(v + 1)) {
                iv.of(v).cover(blockStart);
            }
            for (Insn i : b.insns) {
                for (int u : i.uses()) {
                    iv.of(u).cover(2 * n);
                }
                int d = i.def();
                if (d >= 0) {
                    iv.of(d).cover(2 * n + 1);
                }
                n++;
            }
            LiveSet out = live.out[b.id];
            for (int v = out.next(0); v >= 0; v = out.next(v + 1)) {
                iv.of(v).cover(blockEnd);
            }
        }
        return iv;
    }

    private Interval of(int vreg) {
        return byVreg.computeIfAbsent(vreg, v -> {
            Interval i = new Interval(v);
            all.add(i);
            return i;
        });
    }

    public Interval get(int vreg) {
        return byVreg.get(vreg);
    }
}
