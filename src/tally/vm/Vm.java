package tally.vm;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The TVM interpreter (docs/TVM.md). 8 registers r0 .. r7, a frame pointer, a stack of
 * {@link #STACK_WORDS} words growing down, and a heap of int arrays addressed by handles.
 * Runtime errors print {@code runtime error: <message>} like the Tally interpreter and end the
 * run with status 1. The errors only a broken compiler can cause (a bad handle, a bad stack
 * access, a cycle limit) use the same format.
 */
public final class Vm {
    public static final int STACK_WORDS = 1 << 20;
    public static final int MAX_ARRAY = 1 << 24;
    public static final int HANDLE_BASE = 0x40000000;
    public static final long DEFAULT_MAX_CYCLES = 2_000_000_000L;

    /** A runtime error inside the VM. */
    public static final class Trap extends RuntimeException {
        public Trap(String message) {
            super(message, null, false, false);
        }
    }

    private final Program p;
    private final PrintStream out;
    private final Input input;
    private final long maxCycles;
    private final int[] reg = new int[8];
    private final int[] stack = new int[STACK_WORDS];
    private final List<int[]> heap = new ArrayList<>();
    private int sp = STACK_WORDS;
    private int fp = STACK_WORDS;
    public long cycles;

    public Vm(Program p, InputStream in, PrintStream out, long maxCycles) {
        this.p = p;
        this.input = new Input(in);
        this.out = out;
        this.maxCycles = maxCycles;
    }

    /** Runs main to its return. Returns 0, or 1 after a runtime error (already printed). */
    public int run() {
        try {
            push(-1); // main returns to this sentinel
            execute(p.entry);
            return 0;
        } catch (Trap t) {
            out.println("runtime error: " + t.getMessage());
            return 1;
        } finally {
            out.flush();
        }
    }

    private void push(int v) {
        if (sp <= 0) throw new Trap("stack overflow");
        stack[--sp] = v;
    }

    private int pop() {
        if (sp >= STACK_WORDS) throw new Trap("invalid stack access");
        return stack[sp++];
    }

    private int address(int offset) {
        long a = (long) fp + offset;
        if (a < sp || a >= STACK_WORDS) throw new Trap("invalid stack access");
        return (int) a;
    }

    private int[] array(int handle) {
        long i = (long) handle - HANDLE_BASE;
        if (i < 0 || i >= heap.size()) throw new Trap("invalid array reference");
        return heap.get((int) i);
    }

    private void execute(int start) {
        int pc = start;
        Op[] ops = p.op;
        while (true) {
            if (pc < 0 || pc >= ops.length) throw new Trap("invalid jump");
            Op op = ops[pc];
            cycles += op.cycles;
            if (cycles > maxCycles) throw new Trap("cycle limit exceeded");
            int a = p.r1[pc];
            int b = p.r2[pc];
            int c = p.r3[pc];
            int k = p.imm[pc];
            pc++;
            switch (op) {
                case LI -> reg[a] = k;
                case MOV -> reg[a] = reg[b];
                case ADD -> reg[a] = reg[b] + reg[c];
                case SUB -> reg[a] = reg[b] - reg[c];
                case MUL -> reg[a] = reg[b] * reg[c];
                case DIV -> {
                    if (reg[c] == 0) throw new Trap("division by zero");
                    reg[a] = reg[b] / reg[c];
                }
                case REM -> {
                    if (reg[c] == 0) throw new Trap("division by zero");
                    reg[a] = reg[b] % reg[c];
                }
                case SLT -> reg[a] = reg[b] < reg[c] ? 1 : 0;
                case SLE -> reg[a] = reg[b] <= reg[c] ? 1 : 0;
                case SGT -> reg[a] = reg[b] > reg[c] ? 1 : 0;
                case SGE -> reg[a] = reg[b] >= reg[c] ? 1 : 0;
                case SEQ -> reg[a] = reg[b] == reg[c] ? 1 : 0;
                case SNE -> reg[a] = reg[b] != reg[c] ? 1 : 0;
                case NEG -> reg[a] = -reg[b];
                case NOT -> reg[a] = reg[b] == 0 ? 1 : 0;
                case LD -> reg[a] = stack[address(k)];
                case ST -> stack[address(k)] = reg[a];
                case PUSH -> push(reg[a]);
                case POP -> reg[a] = pop();
                case NEWARR -> {
                    int n = reg[b];
                    if (n < 0) throw new Trap("negative array size");
                    if (n > MAX_ARRAY) throw new Trap("array too large");
                    heap.add(new int[n]);
                    reg[a] = HANDLE_BASE + heap.size() - 1;
                }
                case ALOAD -> {
                    int[] arr = array(reg[b]);
                    int i = reg[c];
                    if (i < 0 || i >= arr.length) throw new Trap("index out of bounds");
                    reg[a] = arr[i];
                }
                case ASTORE -> {
                    int[] arr = array(reg[a]);
                    int i = reg[b];
                    if (i < 0 || i >= arr.length) throw new Trap("index out of bounds");
                    arr[i] = reg[c];
                }
                case ALEN -> reg[a] = array(reg[b]).length;
                case JMP -> pc = k;
                case BZ -> {
                    if (reg[a] == 0) pc = k;
                }
                case BNZ -> {
                    if (reg[a] != 0) pc = k;
                }
                case CALL -> {
                    push(pc);
                    pc = k;
                }
                case RET -> {
                    pc = pop();
                    if (pc == -1) return;
                }
                case ENTER -> {
                    push(fp);
                    fp = sp;
                    if (k < 0 || sp - k < 0) throw new Trap("stack overflow");
                    sp -= k;
                }
                case LEAVE -> {
                    sp = fp;
                    fp = pop();
                }
                case ADDSP -> {
                    if (k < 0 || sp + k > STACK_WORDS) throw new Trap("invalid stack access");
                    sp += k;
                }
                case READ -> reg[a] = input.next();
                case PRINT -> out.println(reg[a]);
                case HALT -> {
                    return;
                }
            }
        }
    }
}
