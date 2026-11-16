package tally.vm;

/** An assembled TVM program: parallel arrays, 1 entry per instruction. */
public final class Program {
    public final Op[] op;
    public final int[] r1;
    public final int[] r2;
    public final int[] r3;
    public final int[] imm;
    public int entry;

    Program(int n) {
        op = new Op[n];
        r1 = new int[n];
        r2 = new int[n];
        r3 = new int[n];
        imm = new int[n];
    }

    public int size() {
        return op.length;
    }
}
