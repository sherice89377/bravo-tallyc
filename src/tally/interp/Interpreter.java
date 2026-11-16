package tally.interp;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tally.front.Ast;

/**
 * The reference semantics of Tally: a direct tree-walking interpreter over the checked syntax tree.
 * Everything the compiler produces must behave exactly like this (docs/LANGUAGE.md section 4).
 *
 * Values are Integer (int) or int[] (array). Integer arithmetic is Java int arithmetic: it wraps
 * modulo 2^32, division truncates toward zero and the remainder takes the sign of the dividend.
 */
public final class Interpreter {
    public static final int MAX_ARRAY = 1 << 24;

    /** A runtime error: the program stops and prints {@code runtime error: <message>}. */
    public static final class Fault extends RuntimeException {
        public Fault(String message) {
            super(message, null, false, false);
        }
    }

    private static final class ReturnSignal extends RuntimeException {
        final int value;

        ReturnSignal(int value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    private final Map<String, Ast.FnDecl> functions = new HashMap<>();
    private final PrintStream out;
    private final Input input;

    public Interpreter(Ast.Program program, InputStream in, PrintStream out) {
        for (Ast.FnDecl f : program.functions) {
            functions.put(f.name, f);
        }
        this.out = out;
        this.input = new Input(in);
    }

    /** Runs main. Returns the exit status: 0, or 1 after a runtime error (already printed). */
    public int run() {
        try {
            call(functions.get("main"), new Object[0]);
            return 0;
        } catch (Fault f) {
            out.println("runtime error: " + f.getMessage());
            return 1;
        } catch (StackOverflowError e) {
            out.println("runtime error: stack overflow");
            return 1;
        } finally {
            out.flush();
        }
    }

    private int call(Ast.FnDecl f, Object[] args) {
        Object[] frame = new Object[f.slots];
        System.arraycopy(args, 0, frame, 0, args.length);
        try {
            block(f.body, frame);
        } catch (ReturnSignal r) {
            return r.value;
        }
        return 0;
    }

    private void block(Ast.Block b, Object[] frame) {
        for (Ast.Stmt s : b.stmts) {
            statement(s, frame);
        }
    }

    private void statement(Ast.Stmt s, Object[] frame) {
        if (s instanceof Ast.Block b) {
            block(b, frame);
        } else if (s instanceof Ast.VarDecl v) {
            frame[v.slot] = eval(v.init, frame);
        } else if (s instanceof Ast.Assign a) {
            frame[a.slot] = eval(a.value, frame);
        } else if (s instanceof Ast.ArrayStore st) {
            int[] arr = (int[]) eval(st.array, frame);
            int index = (Integer) eval(st.index, frame);
            int value = (Integer) eval(st.value, frame);
            if (index < 0 || index >= arr.length) throw new Fault("index out of bounds");
            arr[index] = value;
        } else if (s instanceof Ast.If i) {
            if ((Integer) eval(i.cond, frame) != 0) {
                block(i.then, frame);
            } else if (i.otherwise != null) {
                block(i.otherwise, frame);
            }
        } else if (s instanceof Ast.While w) {
            while ((Integer) eval(w.cond, frame) != 0) {
                block(w.body, frame);
            }
        } else if (s instanceof Ast.Return r) {
            throw new ReturnSignal(r.value == null ? 0 : (Integer) eval(r.value, frame));
        } else if (s instanceof Ast.Print p) {
            out.println((int) (Integer) eval(p.value, frame));
        } else if (s instanceof Ast.ExprStmt e) {
            eval(e.expr, frame);
        } else {
            throw new IllegalStateException("unknown statement");
        }
    }

    private Object eval(Ast.Expr e, Object[] frame) {
        if (e instanceof Ast.IntLit lit) {
            return lit.value;
        }
        if (e instanceof Ast.Name n) {
            return frame[n.slot];
        }
        if (e instanceof Ast.Binary b) {
            int l = (Integer) eval(b.left, frame);
            if (b.op == Ast.BinOp.AND) {
                return l != 0 && (Integer) eval(b.right, frame) != 0 ? 1 : 0;
            }
            if (b.op == Ast.BinOp.OR) {
                return l != 0 || (Integer) eval(b.right, frame) != 0 ? 1 : 0;
            }
            int r = (Integer) eval(b.right, frame);
            return binary(b.op, l, r);
        }
        if (e instanceof Ast.Unary u) {
            int v = (Integer) eval(u.operand, frame);
            return u.op == Ast.UnOp.NEG ? -v : (v == 0 ? 1 : 0);
        }
        if (e instanceof Ast.Call c) {
            Ast.FnDecl f = functions.get(c.callee);
            List<Ast.Expr> argExprs = c.args;
            Object[] args = new Object[argExprs.size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = eval(argExprs.get(i), frame);
            }
            return call(f, args);
        }
        if (e instanceof Ast.Index ix) {
            int[] arr = (int[]) eval(ix.array, frame);
            int index = (Integer) eval(ix.index, frame);
            if (index < 0 || index >= arr.length) throw new Fault("index out of bounds");
            return arr[index];
        }
        if (e instanceof Ast.NewArray na) {
            int size = (Integer) eval(na.size, frame);
            if (size < 0) throw new Fault("negative array size");
            if (size > MAX_ARRAY) throw new Fault("array too large");
            return new int[size];
        }
        if (e instanceof Ast.Read) {
            return input.next();
        }
        if (e instanceof Ast.Len l) {
            return ((int[]) eval(l.array, frame)).length;
        }
        throw new IllegalStateException("unknown expression");
    }

    /** The meaning of every binary operator except the short-circuit ones. */
    public static int binary(Ast.BinOp op, int l, int r) {
        return switch (op) {
            case ADD -> l + r;
            case SUB -> l - r;
            case MUL -> l * r;
            case DIV -> {
                if (r == 0) throw new Fault("division by zero");
                yield l / r;
            }
            case REM -> {
                if (r == 0) throw new Fault("division by zero");
                yield l % r;
            }
            case LT -> l < r ? 1 : 0;
            case LE -> l <= r ? 1 : 0;
            case GT -> l > r ? 1 : 0;
            case GE -> l >= r ? 1 : 0;
            case EQ -> l == r ? 1 : 0;
            case NE -> l != r ? 1 : 0;
            case AND, OR -> throw new IllegalStateException("short-circuit operator");
        };
    }
}
