package tally.backend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import tally.backend.Intervals.Interval;
import tally.ir.Function;

/**
 * Linear-scan register allocation (Poletto and Sarkar). Registers r1 .. r6 are allocatable; r0
 * carries return values and, with r7, is a scratch register for spill code, so neither ever holds a
 * vreg. When no register is free, the interval that ends last is spilled: the current 1, or an
 * active 1 whose register the current interval then takes.
 *
 * Calling convention: the callee saves and restores every allocatable register it writes, so a
 * caller's values in r1 .. r6 survive a call. Parameters arrive on the stack.
 */
public final class LinearScan {
    public static final int FIRST_REG = 1;
    public static final int LAST_REG = 6;

    public final Map<Integer, Location> where = new HashMap<>();
    /** The allocatable registers this function writes, ascending: the prologue saves exactly these. */
    public final List<Integer> savedRegs = new ArrayList<>();
    public int spillSlots;
    public final Intervals intervals;

    private LinearScan(Intervals intervals) {
        this.intervals = intervals;
    }

    public static LinearScan allocate(Function f) {
        Intervals iv = Intervals.build(f);
        LinearScan ls = new LinearScan(iv);
        ls.run(f);
        return ls;
    }

    private void run(Function f) {
        List<Interval> order = new ArrayList<>(intervals.all);
        order.sort(Comparator.comparingInt((Interval i) -> i.start).thenComparingInt(i -> i.vreg));
        TreeSet<Interval> active = new TreeSet<>(
                Comparator.comparingInt((Interval i) -> i.end).thenComparingInt(i -> i.vreg));
        TreeSet<Integer> free = new TreeSet<>();
        for (int r = FIRST_REG; r <= LAST_REG; r++) free.add(r);
        Map<Integer, Integer> regOf = new HashMap<>();

        for (Interval cur : order) {
            // Expire every active interval that ends before this 1 starts.
            while (!active.isEmpty() && active.first().end < cur.start) {
                Interval done = active.pollFirst();
                free.add(regOf.get(done.vreg));
            }
            if (!free.isEmpty()) {
                int r = free.pollFirst();
                regOf.put(cur.vreg, r);
                active.add(cur);
                continue;
            }
            Interval last = active.last();
            if (last.end > cur.end) {
                int r = regOf.remove(last.vreg);
                active.remove(last);
                spill(f, last.vreg);
                regOf.put(cur.vreg, r);
                active.add(cur);
            } else {
                spill(f, cur.vreg);
            }
        }
        for (Map.Entry<Integer, Integer> e : regOf.entrySet()) {
            where.put(e.getKey(), Location.reg(e.getValue()));
        }
        TreeSet<Integer> written = new TreeSet<>();
        for (Map.Entry<Integer, Integer> e : regOf.entrySet()) {
            if (e.getKey() >= f.params) written.add(e.getValue());
        }
        savedRegs.addAll(written);
    }

    private void spill(Function f, int vreg) {
        if (vreg < f.params) {
            where.put(vreg, Location.arg(vreg));
        } else {
            where.put(vreg, Location.slot(++spillSlots));
        }
    }

    public Location locate(int vreg) {
        Location l = where.get(vreg);
        if (l == null) {
            throw new IllegalStateException("v" + vreg + " has no location");
        }
        return l;
    }
}
