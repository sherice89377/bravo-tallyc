package tally.ir;

import java.util.ArrayList;
import java.util.List;

import tally.front.Ast;

/**
 * Lowers a checked syntax tree to IR. Every local slot of a function becomes the vreg with the same
 * number, so parameters are vregs 0 .. n-1. Operands are evaluated left to right, as the interpreter
 * does, and {@code &&} / {@code ||} become branches.
 */
public final class Lowering {
    private Function fn;
    private Block cur;

    public static List<Function> lower(Ast.Program program) {
        List<Function> out = new ArrayList<>();
        for (Ast.FnDecl f : program.functions) {
            out.add(new Lowering().function(f));
        }
        return out;
    }

    private Function function(Ast.FnDecl f) {
        fn = new Function(f.name, f.params.size(), f.returns == Ast.Type.INT, f.slots);
        cur = fn.newBlock();
        block(f.body);
        if (!cur.isTerminated()) {
            if (fn.returnsValue) {
                int zero = fn.newVreg();
                emit(Insn.constant(zero, 0));
                emit(Insn.ret(zero));
            } else {
                emit(Insn.ret(-1));
            }
        }
        fn.computePreds();
        return fn;
    }

    private void emit(Insn i) {
        cur.insns.add(i);
    }

    private void startBlock(Block b) {
        cur = b;
    }

    private void block(Ast.Block b) {
        for (Ast.Stmt s : b.stmts) {
            statement(s);
        }
    }

    private void statement(Ast.Stmt s) {
        if (s instanceof Ast.Block b) {
            block(b);
        } else if (s instanceof Ast.VarDecl v) {
            assign(v.slot, v.init);
        } else if (s instanceof Ast.Assign a) {
            assign(a.slot, a.value);
        } else if (s instanceof Ast.ArrayStore st) {
            int arr = expr(st.array);
            int idx = expr(st.index);
            int val = expr(st.value);
            emit(Insn.astore(arr, idx, val));
        } else if (s instanceof Ast.If i) {
            int c = expr(i.cond);
            Block then = fn.newBlock();
            Block join = fn.newBlock();
            Block otherwise = i.otherwise != null ? fn.newBlock() : join;
            emit(Insn.br(c, then, otherwise));
            startBlock(then);
            block(i.then);
            if (!cur.isTerminated()) emit(Insn.jmp(join));
            if (i.otherwise != null) {
                startBlock(otherwise);
                block(i.otherwise);
                if (!cur.isTerminated()) emit(Insn.jmp(join));
            }
            startBlock(join);
        } else if (s instanceof Ast.While w) {
            Block head = fn.newBlock();
            Block body = fn.newBlock();
            Block exit = fn.newBlock();
            emit(Insn.jmp(head));
            startBlock(head);
            int c = expr(w.cond);
            emit(Insn.br(c, body, exit));
            startBlock(body);
            block(w.body);
            if (!cur.isTerminated()) emit(Insn.jmp(head));
            startBlock(exit);
        } else if (s instanceof Ast.Return r) {
            emit(Insn.ret(r.value == null ? -1 : expr(r.value)));
            startBlock(fn.newBlock()); // anything after a return is unreachable
        } else if (s instanceof Ast.Print p) {
            emit(Insn.print(expr(p.value)));
        } else if (s instanceof Ast.ExprStmt e) {
            if (e.expr instanceof Ast.Call c) {
                call(c, false);
            } else {
                expr(e.expr);
            }
        } else {
            throw new IllegalStateException("unknown statement");
        }
    }

    private void assign(int slot, Ast.Expr value) {
        int v = expr(value);
        if (v != slot) emit(Insn.mov(slot, v));
    }

    private int expr(Ast.Expr e) {
        if (e instanceof Ast.IntLit lit) {
            int t = fn.newVreg();
            emit(Insn.constant(t, lit.value));
            return t;
        }
        if (e instanceof Ast.Name n) {
            return n.slot;
        }
        if (e instanceof Ast.Binary b) {
            if (b.op == Ast.BinOp.AND || b.op == Ast.BinOp.OR) {
                return shortCircuit(b);
            }
            int l = expr(b.left);
            int r = expr(b.right);
            int t = fn.newVreg();
            emit(Insn.bin(bop(b.op), t, l, r));
            return t;
        }
        if (e instanceof Ast.Unary u) {
            int v = expr(u.operand);
            int t = fn.newVreg();
            emit(Insn.unary(u.op == Ast.UnOp.NEG ? Insn.Op.NEG : Insn.Op.NOT, t, v));
            return t;
        }
        if (e instanceof Ast.Call c) {
            return call(c, true);
        }
        if (e instanceof Ast.Index ix) {
            int arr = expr(ix.array);
            int idx = expr(ix.index);
            int t = fn.newVreg();
            emit(Insn.aload(t, arr, idx));
            return t;
        }
        if (e instanceof Ast.NewArray na) {
            int size = expr(na.size);
            int t = fn.newVreg();
            emit(Insn.newArray(t, size));
            return t;
        }
        if (e instanceof Ast.Read) {
            int t = fn.newVreg();
            emit(Insn.read(t));
            return t;
        }
        if (e instanceof Ast.Len l) {
            int arr = expr(l.array);
            int t = fn.newVreg();
            emit(Insn.alen(t, arr));
            return t;
        }
        throw new IllegalStateException("unknown expression");
    }

    private int call(Ast.Call c, boolean wantValue) {
        int[] args = new int[c.args.size()];
        for (int k = 0; k < args.length; k++) {
            args[k] = expr(c.args.get(k));
        }
        int dst = wantValue && c.type == Ast.Type.INT ? fn.newVreg() : -1;
        emit(Insn.call(dst, c.callee, args));
        return dst;
    }

    /** {@code l && r} is 1 when both are non-zero; {@code l || r} is 1 when either is. r runs only when needed. */
    private int shortCircuit(Ast.Binary b) {
        boolean and = b.op == Ast.BinOp.AND;
        int t = fn.newVreg();
        emit(Insn.constant(t, and ? 0 : 1));
        int l = expr(b.left);
        Block right = fn.newBlock();
        Block join = fn.newBlock();
        emit(and ? Insn.br(l, right, join) : Insn.br(l, join, right));
        startBlock(right);
        int r = expr(b.right);
        int zero = fn.newVreg();
        emit(Insn.constant(zero, 0));
        emit(Insn.bin(Insn.BinOp.NE, t, r, zero));
        emit(Insn.jmp(join));
        startBlock(join);
        return t;
    }

    private static Insn.BinOp bop(Ast.BinOp op) {
        return switch (op) {
            case ADD -> Insn.BinOp.ADD;
            case SUB -> Insn.BinOp.SUB;
            case MUL -> Insn.BinOp.MUL;
            case DIV -> Insn.BinOp.DIV;
            case REM -> Insn.BinOp.REM;
            case LT -> Insn.BinOp.LT;
            case LE -> Insn.BinOp.LE;
            case GT -> Insn.BinOp.GT;
            case GE -> Insn.BinOp.GE;
            case EQ -> Insn.BinOp.EQ;
            case NE -> Insn.BinOp.NE;
            case AND, OR -> throw new IllegalStateException("short-circuit operator");
        };
    }
}
