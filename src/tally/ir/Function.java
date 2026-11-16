package tally.ir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 1 function in IR form. Vregs 0 .. params-1 hold the parameters on entry; the other source
 * variables and the temporaries follow. {@code blocks.get(0)} is the entry block.
 */
public final class Function {
    public final String name;
    public final int params;
    public final boolean returnsValue;
    public final List<Block> blocks = new ArrayList<>();
    private int vregs;
    private int nextBlock;

    public Function(String name, int params, boolean returnsValue, int locals) {
        this.name = name;
        this.params = params;
        this.returnsValue = returnsValue;
        this.vregs = locals;
    }

    public int newVreg() {
        return vregs++;
    }

    public int vregCount() {
        return vregs;
    }

    public Block newBlock() {
        Block b = new Block(nextBlock++);
        blocks.add(b);
        return b;
    }

    public Block entry() {
        return blocks.get(0);
    }

    /** Recomputes every block's predecessor list from the terminators. */
    public void computePreds() {
        for (Block b : blocks) {
            b.preds.clear();
        }
        for (Block b : blocks) {
            for (Block s : b.succs()) {
                s.preds.add(b);
            }
        }
    }

    /** Blocks reachable from the entry in reverse postorder (a loop header comes before its body). */
    public List<Block> reversePostorder() {
        List<Block> post = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Object[]> stack = new ArrayDeque<>();
        stack.push(new Object[] {entry(), 0});
        seen.add(entry());
        while (!stack.isEmpty()) {
            Object[] top = stack.peek();
            Block b = (Block) top[0];
            int k = (Integer) top[1];
            List<Block> succ = b.succs();
            if (k < succ.size()) {
                top[1] = k + 1;
                Block s = succ.get(k);
                if (seen.add(s)) {
                    stack.push(new Object[] {s, 0});
                }
            } else {
                stack.pop();
                post.add(b);
            }
        }
        List<Block> rpo = new ArrayList<>(post.size());
        for (int i = post.size() - 1; i >= 0; i--) {
            rpo.add(post.get(i));
        }
        return rpo;
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("function ").append(name).append(" (").append(params).append(" params)\n");
        for (Block b : blocks) {
            sb.append("  B").append(b.id).append(":\n");
            for (Insn i : b.insns) {
                sb.append("    ").append(i).append('\n');
            }
        }
        return sb.toString();
    }
}
