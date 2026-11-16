package tally.opt;

import tally.ir.Function;

/**
 * The optimisation pipeline. Level 0 only tidies the CFG (the register allocator needs every block
 * reachable). Level 2, the default: loop inversion, then SSA form, where constant propagation,
 * copy propagation and dead-code elimination repeat until nothing changes (at most 8 rounds), then
 * back out of SSA form, copy coalescing and a last dead-code pass.
 */
public final class Pipeline {
    public static final int MAX_ROUNDS = 8;

    private Pipeline() {}

    public static void optimise(Function f, int level) {
        Cleanup.run(f);
        if (level <= 0) {
            return;
        }
        LoopInversion.run(f);
        Cleanup.run(f);
        Ssa.build(f);
        for (int round = 0; round < MAX_ROUNDS; round++) {
            boolean changed = false;
            changed |= Sccp.run(f);
            changed |= Cleanup.run(f);
            changed |= SsaCopyProp.run(f);
            changed |= SsaDce.run(f);
            if (!changed) {
                break;
            }
        }
        OutOfSsa.run(f);
        Cleanup.run(f);
        DeadCode.run(f);
        Coalesce.run(f);
        DeadCode.run(f);
    }
}
