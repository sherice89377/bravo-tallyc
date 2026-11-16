package tally.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads TVM assembly text (docs/TVM.md) into a {@link Program}. 1 instruction or label per line;
 * {@code ;} starts a comment. Labels end with {@code :}; a function is the label with its name.
 */
public final class Assembler {
    /** A malformed assembly file. */
    public static final class AsmError extends RuntimeException {
        public AsmError(int line, String message) {
            super("line " + line + ": " + message);
        }
    }

    private Assembler() {}

    public static Program assemble(String text) {
        String[] lines = text.split("\n", -1);
        Map<String, Integer> labels = new HashMap<>();
        List<String[]> parts = new ArrayList<>();
        List<Integer> lineNos = new ArrayList<>();
        for (int n = 0; n < lines.length; n++) {
            String s = lines[n];
            int semi = s.indexOf(';');
            if (semi >= 0) s = s.substring(0, semi);
            s = s.trim();
            if (s.isEmpty()) continue;
            if (s.endsWith(":")) {
                String name = s.substring(0, s.length() - 1).trim();
                if (labels.put(name, parts.size()) != null) {
                    throw new AsmError(n + 1, "label '" + name + "' defined more than 1 time");
                }
                continue;
            }
            int sp = s.indexOf(' ');
            String op = sp < 0 ? s : s.substring(0, sp);
            String rest = sp < 0 ? "" : s.substring(sp + 1).trim();
            List<String> fields = new ArrayList<>();
            fields.add(op);
            if (!rest.isEmpty()) {
                for (String f : rest.split(",")) fields.add(f.trim());
            }
            parts.add(fields.toArray(new String[0]));
            lineNos.add(n + 1);
        }
        Program p = new Program(parts.size());
        for (int k = 0; k < parts.size(); k++) {
            encode(p, k, parts.get(k), lineNos.get(k), labels);
        }
        Integer main = labels.get("main");
        if (main == null) throw new AsmError(1, "no 'main' label");
        p.entry = main;
        return p;
    }

    private static void encode(Program p, int k, String[] f, int line, Map<String, Integer> labels) {
        Op op = Op.byName(f[0]);
        if (op == null) throw new AsmError(line, "unknown instruction '" + f[0] + "'");
        p.op[k] = op;
        int want = op.operands.length();
        if (f.length - 1 != want) {
            throw new AsmError(line, f[0] + " takes " + want + " operands");
        }
        int regSlot = 0;
        for (int i = 0; i < want; i++) {
            String s = f[i + 1];
            switch (op.operands.charAt(i)) {
                case 'r' -> {
                    int r = reg(s, line);
                    if (regSlot == 0) p.r1[k] = r;
                    else if (regSlot == 1) p.r2[k] = r;
                    else p.r3[k] = r;
                    regSlot++;
                }
                case 'i' -> p.imm[k] = integer(s, line);
                case 'm' -> p.imm[k] = memory(s, line);
                case 'l' -> {
                    Integer t = labels.get(s);
                    if (t == null) throw new AsmError(line, "unknown label '" + s + "'");
                    p.imm[k] = t;
                }
                default -> throw new IllegalStateException();
            }
        }
    }

    private static int reg(String s, int line) {
        if (s.length() == 2 && s.charAt(0) == 'r' && s.charAt(1) >= '0' && s.charAt(1) <= '7') {
            return s.charAt(1) - '0';
        }
        throw new AsmError(line, "expected a register r0 .. r7, found '" + s + "'");
    }

    private static int integer(String s, int line) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new AsmError(line, "expected an integer, found '" + s + "'");
        }
    }

    /** {@code [fp+k]} or {@code [fp-k]}: the signed offset k. */
    private static int memory(String s, int line) {
        if (!s.startsWith("[fp") || !s.endsWith("]") || s.length() < 6) {
            throw new AsmError(line, "expected [fp+k] or [fp-k], found '" + s + "'");
        }
        char sign = s.charAt(3);
        int k = integer(s.substring(4, s.length() - 1), line);
        if (sign == '+') return k;
        if (sign == '-') return -k;
        throw new AsmError(line, "expected [fp+k] or [fp-k], found '" + s + "'");
    }
}
