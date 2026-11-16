package tally.front;

/** 1 lexical token. {@code text} is the source spelling; {@code value} is set for integer literals. */
public final class Token {
    public enum Kind {
        IDENT, INT, EOF,
        FN, VAR, IF, ELSE, WHILE, RETURN, PRINT, NEW, INT_TYPE, LEN, READ,
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET, COMMA, SEMI, COLON,
        ASSIGN, PLUS, MINUS, STAR, SLASH, PERCENT,
        EQ, NE, LT, LE, GT, GE, AND, OR, NOT
    }

    public final Kind kind;
    public final String text;
    public final long value;
    public final int line;
    public final int column;

    public Token(Kind kind, String text, long value, int line, int column) {
        this.kind = kind;
        this.text = text;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return kind + "(" + text + ")@" + line + ":" + column;
    }
}
