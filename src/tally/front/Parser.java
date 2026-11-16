package tally.front;

import java.util.ArrayList;
import java.util.List;

import tally.front.Ast.BinOp;
import tally.front.Token.Kind;

/** Recursive-descent parser for Tally. The grammar is in docs/LANGUAGE.md section 2. */
public final class Parser {
    private final List<Token> toks;
    private int pos;

    public Parser(List<Token> toks) {
        this.toks = toks;
    }

    public static Ast.Program parse(String source) {
        return new Parser(new Lexer(source).tokens()).program();
    }

    public Ast.Program program() {
        Ast.Program p = new Ast.Program();
        while (peek().kind != Kind.EOF) {
            p.functions.add(function());
        }
        return p;
    }

    private Ast.FnDecl function() {
        Token at = expect(Kind.FN, "'fn'");
        Token name = expect(Kind.IDENT, "a function name");
        expect(Kind.LPAREN, "'('");
        List<Ast.Param> params = new ArrayList<>();
        if (peek().kind != Kind.RPAREN) {
            do {
                Token pn = expect(Kind.IDENT, "a parameter name");
                expect(Kind.COLON, "':'");
                params.add(new Ast.Param(pn.text, type(), pn));
            } while (accept(Kind.COMMA));
        }
        expect(Kind.RPAREN, "')'");
        Ast.Type returns = Ast.Type.VOID;
        if (accept(Kind.COLON)) {
            returns = type();
            if (returns != Ast.Type.INT) {
                throw new CompileError(at.line, at.column, "a function can return only int");
            }
        }
        return new Ast.FnDecl(name, name.text, params, returns, block());
    }

    private Ast.Type type() {
        expect(Kind.INT_TYPE, "a type");
        if (accept(Kind.LBRACKET)) {
            expect(Kind.RBRACKET, "']'");
            return Ast.Type.ARRAY;
        }
        return Ast.Type.INT;
    }

    private Ast.Block block() {
        Token at = expect(Kind.LBRACE, "'{'");
        Ast.Block b = new Ast.Block(at);
        while (peek().kind != Kind.RBRACE) {
            if (peek().kind == Kind.EOF) {
                throw error(peek(), "unexpected end of file, expected '}'");
            }
            b.stmts.add(statement());
        }
        expect(Kind.RBRACE, "'}'");
        return b;
    }

    private Ast.Stmt statement() {
        Token t = peek();
        switch (t.kind) {
            case VAR -> {
                next();
                Token name = expect(Kind.IDENT, "a variable name");
                Ast.Type declared = null;
                if (accept(Kind.COLON)) {
                    declared = type();
                }
                expect(Kind.ASSIGN, "'='");
                Ast.Expr init = expression();
                expect(Kind.SEMI, "';'");
                return new Ast.VarDecl(name, name.text, declared, init);
            }
            case IF -> {
                return ifStatement();
            }
            case WHILE -> {
                next();
                expect(Kind.LPAREN, "'('");
                Ast.Expr cond = expression();
                expect(Kind.RPAREN, "')'");
                return new Ast.While(t, cond, block());
            }
            case RETURN -> {
                next();
                Ast.Expr value = null;
                if (peek().kind != Kind.SEMI) {
                    value = expression();
                }
                expect(Kind.SEMI, "';'");
                return new Ast.Return(t, value);
            }
            case PRINT -> {
                next();
                expect(Kind.LPAREN, "'('");
                Ast.Expr value = expression();
                expect(Kind.RPAREN, "')'");
                expect(Kind.SEMI, "';'");
                return new Ast.Print(t, value);
            }
            case LBRACE -> {
                return block();
            }
            default -> {
                Ast.Expr target = expression();
                if (accept(Kind.ASSIGN)) {
                    Ast.Expr value = expression();
                    expect(Kind.SEMI, "';'");
                    if (target instanceof Ast.Name n) {
                        return new Ast.Assign(t, n.name, value);
                    }
                    if (target instanceof Ast.Index ix) {
                        return new Ast.ArrayStore(t, ix.array, ix.index, value);
                    }
                    throw error(t, "the left side of '=' must be a variable or an array element");
                }
                expect(Kind.SEMI, "';'");
                if (!(target instanceof Ast.Call)) {
                    throw error(t, "an expression statement must be a call");
                }
                return new Ast.ExprStmt(t, target);
            }
        }
    }

    private Ast.Stmt ifStatement() {
        Token t = expect(Kind.IF, "'if'");
        expect(Kind.LPAREN, "'('");
        Ast.Expr cond = expression();
        expect(Kind.RPAREN, "')'");
        Ast.Block then = block();
        Ast.Block otherwise = null;
        if (accept(Kind.ELSE)) {
            if (peek().kind == Kind.IF) {
                Token at = peek();
                otherwise = new Ast.Block(at);
                otherwise.stmts.add(ifStatement());
            } else {
                otherwise = block();
            }
        }
        return new Ast.If(t, cond, then, otherwise);
    }

    // ---- expressions, lowest precedence first --------------------------------------------

    private Ast.Expr expression() {
        return or();
    }

    private Ast.Expr or() {
        Ast.Expr e = and();
        while (peek().kind == Kind.OR) {
            Token t = next();
            e = new Ast.Binary(t, BinOp.OR, e, and());
        }
        return e;
    }

    private Ast.Expr and() {
        Ast.Expr e = equality();
        while (peek().kind == Kind.AND) {
            Token t = next();
            e = new Ast.Binary(t, BinOp.AND, e, equality());
        }
        return e;
    }

    private Ast.Expr equality() {
        Ast.Expr e = relational();
        while (peek().kind == Kind.EQ || peek().kind == Kind.NE) {
            Token t = next();
            e = new Ast.Binary(t, t.kind == Kind.EQ ? BinOp.EQ : BinOp.NE, e, relational());
        }
        return e;
    }

    private Ast.Expr relational() {
        Ast.Expr e = additive();
        while (true) {
            Kind k = peek().kind;
            BinOp op = switch (k) {
                case LT -> BinOp.LT;
                case LE -> BinOp.LE;
                case GT -> BinOp.GT;
                case GE -> BinOp.GE;
                default -> null;
            };
            if (op == null) return e;
            Token t = next();
            e = new Ast.Binary(t, op, e, additive());
        }
    }

    private Ast.Expr additive() {
        Ast.Expr e = multiplicative();
        while (peek().kind == Kind.PLUS || peek().kind == Kind.MINUS) {
            Token t = next();
            e = new Ast.Binary(t, t.kind == Kind.PLUS ? BinOp.ADD : BinOp.SUB, e, multiplicative());
        }
        return e;
    }

    private Ast.Expr multiplicative() {
        Ast.Expr e = unary();
        while (true) {
            Kind k = peek().kind;
            BinOp op = switch (k) {
                case STAR -> BinOp.MUL;
                case SLASH -> BinOp.DIV;
                case PERCENT -> BinOp.REM;
                default -> null;
            };
            if (op == null) return e;
            Token t = next();
            e = new Ast.Binary(t, op, e, unary());
        }
    }

    private Ast.Expr unary() {
        Token t = peek();
        if (accept(Kind.MINUS)) {
            return new Ast.Unary(t, Ast.UnOp.NEG, unary());
        }
        if (accept(Kind.NOT)) {
            return new Ast.Unary(t, Ast.UnOp.NOT, unary());
        }
        return postfix();
    }

    private Ast.Expr postfix() {
        Ast.Expr e = primary();
        while (peek().kind == Kind.LBRACKET) {
            Token t = next();
            Ast.Expr index = expression();
            expect(Kind.RBRACKET, "']'");
            e = new Ast.Index(t, e, index);
        }
        return e;
    }

    private Ast.Expr primary() {
        Token t = next();
        switch (t.kind) {
            case INT -> {
                return new Ast.IntLit(t, (int) t.value);
            }
            case IDENT -> {
                if (accept(Kind.LPAREN)) {
                    List<Ast.Expr> args = new ArrayList<>();
                    if (peek().kind != Kind.RPAREN) {
                        do {
                            args.add(expression());
                        } while (accept(Kind.COMMA));
                    }
                    expect(Kind.RPAREN, "')'");
                    return new Ast.Call(t, t.text, args);
                }
                return new Ast.Name(t, t.text);
            }
            case LPAREN -> {
                Ast.Expr e = expression();
                expect(Kind.RPAREN, "')'");
                return e;
            }
            case NEW -> {
                expect(Kind.INT_TYPE, "'int'");
                expect(Kind.LBRACKET, "'['");
                Ast.Expr size = expression();
                expect(Kind.RBRACKET, "']'");
                return new Ast.NewArray(t, size);
            }
            case READ -> {
                expect(Kind.LPAREN, "'('");
                expect(Kind.RPAREN, "')'");
                return new Ast.Read(t);
            }
            case LEN -> {
                expect(Kind.LPAREN, "'('");
                Ast.Expr a = expression();
                expect(Kind.RPAREN, "')'");
                return new Ast.Len(t, a);
            }
            default -> throw error(t, "unexpected '" + t.text + "'");
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private Token peek() {
        return toks.get(pos);
    }

    private Token next() {
        Token t = toks.get(pos);
        if (t.kind != Kind.EOF) pos++;
        return t;
    }

    private boolean accept(Kind k) {
        if (peek().kind == k) {
            next();
            return true;
        }
        return false;
    }

    private Token expect(Kind k, String what) {
        Token t = peek();
        if (t.kind != k) {
            throw error(t, "expected " + what + " but found '" + (t.kind == Kind.EOF ? "end of file" : t.text) + "'");
        }
        return next();
    }

    private static CompileError error(Token t, String msg) {
        return new CompileError(t.line, t.column, msg);
    }
}
