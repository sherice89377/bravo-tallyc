package tally.ir;

import java.util.ArrayList;
import java.util.List;

/** A basic block: instructions in order, the last 1 a terminator (RET, JMP or BR). */
public final class Block {
    public final int id;
    public final List<Insn> insns = new ArrayList<>();
    public final List<Block> preds = new ArrayList<>();

    public Block(int id) {
        this.id = id;
    }

    public Insn terminator() {
        return insns.isEmpty() ? null : insns.get(insns.size() - 1);
    }

    public boolean isTerminated() {
        Insn t = terminator();
        return t != null && t.isTerminator();
    }

    /** Successors in branch order: target first, then other. */
    public List<Block> succs() {
        Insn t = terminator();
        List<Block> out = new ArrayList<>(2);
        if (t == null) return out;
        switch (t.op) {
            case JMP -> out.add(t.target);
            case BR -> {
                out.add(t.target);
                if (t.other != t.target) out.add(t.other);
            }
            default -> { }
        }
        return out;
    }

    @Override
    public String toString() {
        return "B" + id;
    }
}
