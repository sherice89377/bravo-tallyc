package tally.opt;

/**
 * A growable set of vreg numbers for the data-flow passes, 1 bit per vreg in 64-bit words. Smaller
 * and faster than java.util.BitSet for these passes: no bounds checks on reads past the end, and
 * {@link #addAll} reports whether anything was added.
 */
public final class LiveSet {
    private long[] words;

    /** Room for vregs 0 .. 511 to start with, which covers most functions without growing. */
    public LiveSet() {
        words = new long[8];
    }

    private LiveSet(long[] words) {
        this.words = words;
    }

    public boolean get(int v) {
        int w = v >>> 6;
        return w < words.length && (words[w] & (1L << v)) != 0;
    }

    public void set(int v) {
        int w = v >>> 6;
        if (w >= words.length) {
            long[] grown = new long[Math.max(w + 1, words.length * 2)];
            System.arraycopy(words, 0, grown, 0, words.length);
            words = grown;
        }
        words[w] |= 1L << v;
    }

    public void clear(int v) {
        int w = v >>> 6;
        if (w < words.length) words[w] &= ~(1L << v);
    }

    /** Adds every member of {@code o}. Returns true when this set gained a member. */
    public boolean addAll(LiveSet o) {
        boolean changed = false;
        for (int w = 0; w < words.length && w < o.words.length; w++) {
            long add = o.words[w] & ~words[w];
            if (add != 0) {
                words[w] |= add;
                changed = true;
            }
        }
        return changed;
    }

    /** Removes every member of {@code o}. */
    public void removeAll(LiveSet o) {
        int n = Math.min(words.length, o.words.length);
        for (int w = 0; w < n; w++) words[w] &= ~o.words[w];
    }

    /** The smallest member at least {@code from}, or -1. */
    public int next(int from) {
        if (from < 0) return -1;
        int w = from >>> 6;
        if (w >= words.length) return -1;
        long cur = words[w] & (-1L << from);
        while (true) {
            if (cur != 0) return (w << 6) + Long.numberOfTrailingZeros(cur);
            if (++w >= words.length) return -1;
            cur = words[w];
        }
    }

    public LiveSet copy() {
        return new LiveSet(words.clone());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (int v = next(0); v >= 0; v = next(v + 1)) {
            if (sb.length() > 1) sb.append(", ");
            sb.append('v').append(v);
        }
        return sb.append('}').toString();
    }
}
