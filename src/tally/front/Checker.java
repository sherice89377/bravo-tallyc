package tally.front;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tally.front.Ast.Type;

/**
 * Name resolution and type checking (docs/LANGUAGE.md section 3). Gives every local a slot in its
 * function, parameters first, and every expression its type. A name may not shadow another name that
 * is visible at its declaration; sibling blocks may reuse a name (each declaration gets a new slot).
 */
public final class Checker {
    private final Ast.Program program;
    private final Map<String, Ast.FnDecl> functions = new HashMap<>();
    private Ast.FnDecl current;
    private final Deque<Map<String, Integer>> scopes = new ArrayDeque<>();

    private Checker(Ast.Program program) {
        this.program = program;
    }

    public static void check(Ast.Program program) {
        new Checker(program).run();
    }

    private void run() {
        for (Ast.FnDecl f : program.functions) {
            if (functions.put(f.name, f) != null) {
                throw new CompileError(f.line, f.column, "function '" + f.name + "' is defined more than 1 time");
            }
        }
        Ast.FnDecl main = functions.get("main");
        if (main == null) {
            throw new CompileError(1, 1, "the program has no function 'main'");
        }
        if (!main.params.isEmpty() || main.returns != Type.VOID) {
            throw new CompileError(main.line, main.column, "'main' must take no parameters and return nothing");
        }
        for (Ast.FnDecl f : program.functions) {
            function(f);
        }
    }

    private void function(Ast.FnDecl f) {
        current = f;
        f.slots = 0;
        f.slotTypes.clear();
        scopes.clear();
        scopes.push(new HashMap<>());
        for (Ast.Param p : f.params) {
            declare(p.name, p.type, p.at.line, p.at.column);
        }
        block(f.body);
        scopes.pop();
    }

    private int declare(String name, Type type, int line, int column) {
        if (lookup(name) != null) {
            throw new CompileError(line, column, "'" + name + "' is already declared");
        }
        int slot = current.slots++;
        current.slotTypes.add(type);
        scopes.peek().put(name, slot);
        return slot;
    }

    private Integer lookup(String name) {
        for (Map<String, Integer> s : scopes) {
            Integer slot = s.get(name);
            if (slot != null) return slot;
        }
        return null;
    }

    private int resolve(String name, Ast.Node at) {
        Integer slot = lookup(name);
        if (slot == null) {
            throw new CompileError(at.line, at.column, "'" + name + "' is not declared");
        }
        return slot;
    }

    private void block(Ast.Block b) {
        scopes.push(new HashMap<>());
        for (Ast.Stmt s : b.stmts) {
            statement(s);
        }
        scopes.pop();
    }

    private void statement(Ast.Stmt s) {
        if (s instanceof Ast.Block b) {
            block(b);
        } else if (s instanceof Ast.VarDecl v) {
            Type t = expr(v.init);
            if (t == Type.VOID) {
                throw new CompileError(v.line, v.column, "'" + v.name + "' needs a value, but the call returns nothing");
            }
            if (v.declared != null && v.declared != t) {
                throw new CompileError(v.line, v.column, "'" + v.name + "' is declared " + name(v.declared) + " but its value is " + name(t));
            }
            v.slot = declare(v.name, t, v.line, v.column);
        } else if (s instanceof Ast.Assign a) {
            a.slot = resolve(a.name, a);
            Type want = current.slotTypes.get(a.slot);
            Type got = expr(a.value);
            if (got != want) {
                throw new CompileError(a.line, a.column, "cannot assign " + name(got) + " to '" + a.name + "' (" + name(want) + ")");
            }
        } else if (s instanceof Ast.ArrayStore st) {
            want(st.array, Type.ARRAY, "an indexed value");
            want(st.index, Type.INT, "an index");
            want(st.value, Type.INT, "an array element");
        } else if (s instanceof Ast.If i) {
            want(i.cond, Type.INT, "a condition");
            block(i.then);
            if (i.otherwise != null) block(i.otherwise);
        } else if (s instanceof Ast.While w) {
            want(w.cond, Type.INT, "a condition");
            block(w.body);
        } else if (s instanceof Ast.Return r) {
            if (current.returns == Type.VOID) {
                if (r.value != null) {
                    throw new CompileError(r.line, r.column, "'" + current.name + "' returns nothing");
                }
            } else {
                if (r.value == null) {
                    throw new CompileError(r.line, r.column, "'" + current.name + "' must return an int");
                }
                want(r.value, Type.INT, "a return value");
            }
        } else if (s instanceof Ast.Print p) {
            want(p.value, Type.INT, "a printed value");
        } else if (s instanceof Ast.ExprStmt e) {
            expr(e.expr);
        } else {
            throw new IllegalStateException("unknown statement " + s.getClass().getSimpleName());
        }
    }

    private void want(Ast.Expr e, Type t, String what) {
        Type got = expr(e);
        if (got != t) {
            throw new CompileError(e.line, e.column, what + " must be " + name(t) + ", not " + name(got));
        }
    }

    private Type expr(Ast.Expr e) {
        e.type = typeOf(e);
        return e.type;
    }

    private Type typeOf(Ast.Expr e) {
        if (e instanceof Ast.IntLit) {
            return Type.INT;
        }
        if (e instanceof Ast.Name n) {
            n.slot = resolve(n.name, n);
            return current.slotTypes.get(n.slot);
        }
        if (e instanceof Ast.Binary b) {
            want(b.left, Type.INT, "an operand of '" + b.op.symbol + "'");
            want(b.right, Type.INT, "an operand of '" + b.op.symbol + "'");
            return Type.INT;
        }
        if (e instanceof Ast.Unary u) {
            want(u.operand, Type.INT, "an operand");
            return Type.INT;
        }
        if (e instanceof Ast.Call c) {
            Ast.FnDecl f = functions.get(c.callee);
            if (f == null) {
                throw new CompileError(c.line, c.column, "function '" + c.callee + "' is not defined");
            }
            if (f.name.equals("main")) {
                throw new CompileError(c.line, c.column, "'main' cannot be called");
            }
            if (f.params.size() != c.args.size()) {
                throw new CompileError(c.line, c.column, "'" + c.callee + "' takes " + f.params.size() + " arguments, not " + c.args.size());
            }
            for (int i = 0; i < c.args.size(); i++) {
                want(c.args.get(i), f.params.get(i).type, "argument " + (i + 1) + " of '" + c.callee + "'");
            }
            return f.returns;
        }
        if (e instanceof Ast.Index ix) {
            want(ix.array, Type.ARRAY, "an indexed value");
            want(ix.index, Type.INT, "an index");
            return Type.INT;
        }
        if (e instanceof Ast.NewArray na) {
            want(na.size, Type.INT, "an array size");
            return Type.ARRAY;
        }
        if (e instanceof Ast.Read) {
            return Type.INT;
        }
        if (e instanceof Ast.Len l) {
            want(l.array, Type.ARRAY, "the operand of len");
            return Type.INT;
        }
        throw new IllegalStateException("unknown expression " + e.getClass().getSimpleName());
    }

    private static String name(Type t) {
        return switch (t) {
            case INT -> "int";
            case ARRAY -> "int[]";
            case VOID -> "nothing";
        };
    }

    /** For tools: the checked functions in source order. */
    public static List<Ast.FnDecl> functions(Ast.Program p) {
        return p.functions;
    }
}
