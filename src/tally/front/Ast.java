package tally.front;

import java.util.ArrayList;
import java.util.List;

/**
 * The syntax tree. Nodes are plain mutable classes: the parser fills the shape, the checker fills
 * {@code type} on expressions and {@code slot} on names and declarations (the local's index in its
 * function, parameters first).
 */
public final class Ast {
    private Ast() {}

    public enum Type { INT, ARRAY, VOID }

    public enum BinOp {
        ADD("+"), SUB("-"), MUL("*"), DIV("/"), REM("%"),
        LT("<"), LE("<="), GT(">"), GE(">="), EQ("=="), NE("!="),
        AND("&&"), OR("||");

        public final String symbol;

        BinOp(String symbol) {
            this.symbol = symbol;
        }
    }

    public enum UnOp { NEG, NOT }

    public abstract static class Node {
        public final int line;
        public final int column;

        Node(Token at) {
            this.line = at.line;
            this.column = at.column;
        }
    }

    public static final class Program {
        public final List<FnDecl> functions = new ArrayList<>();

        public FnDecl find(String name) {
            for (FnDecl f : functions) {
                if (f.name.equals(name)) return f;
            }
            return null;
        }
    }

    public static final class Param {
        public final String name;
        public final Type type;
        public final Token at;

        public Param(String name, Type type, Token at) {
            this.name = name;
            this.type = type;
            this.at = at;
        }
    }

    public static final class FnDecl extends Node {
        public final String name;
        public final List<Param> params;
        public final Type returns;
        public final Block body;
        /** Number of local slots (parameters included), set by the checker. */
        public int slots;
        /** Type of each slot, set by the checker. */
        public final List<Type> slotTypes = new ArrayList<>();

        public FnDecl(Token at, String name, List<Param> params, Type returns, Block body) {
            super(at);
            this.name = name;
            this.params = params;
            this.returns = returns;
            this.body = body;
        }
    }

    // ---- statements ----------------------------------------------------------------------

    public abstract static class Stmt extends Node {
        Stmt(Token at) {
            super(at);
        }
    }

    public static final class Block extends Stmt {
        public final List<Stmt> stmts = new ArrayList<>();

        public Block(Token at) {
            super(at);
        }
    }

    public static final class VarDecl extends Stmt {
        public final String name;
        public final Type declared;
        public final Expr init;
        public int slot = -1;

        public VarDecl(Token at, String name, Type declared, Expr init) {
            super(at);
            this.name = name;
            this.declared = declared;
            this.init = init;
        }
    }

    public static final class Assign extends Stmt {
        public final String name;
        public final Expr value;
        public int slot = -1;

        public Assign(Token at, String name, Expr value) {
            super(at);
            this.name = name;
            this.value = value;
        }
    }

    public static final class ArrayStore extends Stmt {
        public final Expr array;
        public final Expr index;
        public final Expr value;

        public ArrayStore(Token at, Expr array, Expr index, Expr value) {
            super(at);
            this.array = array;
            this.index = index;
            this.value = value;
        }
    }

    public static final class If extends Stmt {
        public final Expr cond;
        public final Block then;
        public final Block otherwise;

        public If(Token at, Expr cond, Block then, Block otherwise) {
            super(at);
            this.cond = cond;
            this.then = then;
            this.otherwise = otherwise;
        }
    }

    public static final class While extends Stmt {
        public final Expr cond;
        public final Block body;

        public While(Token at, Expr cond, Block body) {
            super(at);
            this.cond = cond;
            this.body = body;
        }
    }

    public static final class Return extends Stmt {
        public final Expr value;

        public Return(Token at, Expr value) {
            super(at);
            this.value = value;
        }
    }

    public static final class Print extends Stmt {
        public final Expr value;

        public Print(Token at, Expr value) {
            super(at);
            this.value = value;
        }
    }

    public static final class ExprStmt extends Stmt {
        public final Expr expr;

        public ExprStmt(Token at, Expr expr) {
            super(at);
            this.expr = expr;
        }
    }

    // ---- expressions ---------------------------------------------------------------------

    public abstract static class Expr extends Node {
        public Type type;

        Expr(Token at) {
            super(at);
        }
    }

    public static final class IntLit extends Expr {
        public final int value;

        public IntLit(Token at, int value) {
            super(at);
            this.value = value;
        }
    }

    public static final class Name extends Expr {
        public final String name;
        public int slot = -1;

        public Name(Token at, String name) {
            super(at);
            this.name = name;
        }
    }

    public static final class Binary extends Expr {
        public final BinOp op;
        public final Expr left;
        public final Expr right;

        public Binary(Token at, BinOp op, Expr left, Expr right) {
            super(at);
            this.op = op;
            this.left = left;
            this.right = right;
        }
    }

    public static final class Unary extends Expr {
        public final UnOp op;
        public final Expr operand;

        public Unary(Token at, UnOp op, Expr operand) {
            super(at);
            this.op = op;
            this.operand = operand;
        }
    }

    public static final class Call extends Expr {
        public final String callee;
        public final List<Expr> args;

        public Call(Token at, String callee, List<Expr> args) {
            super(at);
            this.callee = callee;
            this.args = args;
        }
    }

    public static final class Index extends Expr {
        public final Expr array;
        public final Expr index;

        public Index(Token at, Expr array, Expr index) {
            super(at);
            this.array = array;
            this.index = index;
        }
    }

    public static final class NewArray extends Expr {
        public final Expr size;

        public NewArray(Token at, Expr size) {
            super(at);
            this.size = size;
        }
    }

    /** {@code read()}: the next integer of the program's input, or 0 when the input is used up. */
    public static final class Read extends Expr {
        public Read(Token at) {
            super(at);
        }
    }

    public static final class Len extends Expr {
        public final Expr array;

        public Len(Token at, Expr array) {
            super(at);
            this.array = array;
        }
    }
}
