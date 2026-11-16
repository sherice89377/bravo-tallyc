package tally.backend;

/**
 * Where a vreg lives for its whole lifetime: a register, a spill slot in the frame ({@code [fp-k]},
 * k from 1), or, for a spilled parameter, its incoming argument slot ({@code [fp+2+i]}).
 */
public record Location(Kind kind, int index) {
    public enum Kind { REG, SLOT, ARG }

    public static Location reg(int r) {
        return new Location(Kind.REG, r);
    }

    public static Location slot(int k) {
        return new Location(Kind.SLOT, k);
    }

    public static Location arg(int i) {
        return new Location(Kind.ARG, i);
    }

    public boolean inRegister() {
        return kind == Kind.REG;
    }

    /** The VM memory operand for a stack location. */
    public String memory() {
        return switch (kind) {
            case SLOT -> "[fp-" + index + "]";
            case ARG -> "[fp+" + (2 + index) + "]";
            case REG -> throw new IllegalStateException("a register has no memory operand");
        };
    }

    @Override
    public String toString() {
        return kind == Kind.REG ? "r" + index : memory();
    }
}
