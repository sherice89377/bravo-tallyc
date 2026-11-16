package tally.front;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns Tally source text into tokens. Comments run from {@code //} to the end of the line. */
public final class Lexer {
    private static final Map<String, Token.Kind> KEYWORDS = Map.ofEntries(
            Map.entry("fn", Token.Kind.FN),
            Map.entry("var", Token.Kind.VAR),
            Map.entry("if", Token.Kind.IF),
            Map.entry("else", Token.Kind.ELSE),
            Map.entry("while", Token.Kind.WHILE),
            Map.entry("return", Token.Kind.RETURN),
            Map.entry("print", Token.Kind.PRINT),
            Map.entry("new", Token.Kind.NEW),
            Map.entry("int", Token.Kind.INT_TYPE),
            Map.entry("len", Token.Kind.LEN),
            Map.entry("read", Token.Kind.READ));

    private final String src;
    private int pos;
    private int line = 1;
    private int lineStart;

    public Lexer(String src) {
        this.src = src;
    }

    public List<Token> tokens() {
        List<Token> out = new ArrayList<>();
        while (true) {
            skipSpaceAndComments();
            int col = pos - lineStart + 1;
            if (pos >= src.length()) {
                out.add(new Token(Token.Kind.EOF, "", 0, line, col));
                return out;
            }
            char c = src.charAt(pos);
            if (Character.isLetter(c) || c == '_') {
                int start = pos;
                while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
                    pos++;
                }
                String word = src.substring(start, pos);
                out.add(new Token(KEYWORDS.getOrDefault(word, Token.Kind.IDENT), word, 0, line, col));
            } else if (Character.isDigit(c)) {
                int start = pos;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
                String digits = src.substring(start, pos);
                if (digits.length() > 10 || Long.parseLong(digits) > Integer.MAX_VALUE) {
                    throw new CompileError(line, col, "integer literal out of range: " + digits);
                }
                out.add(new Token(Token.Kind.INT, digits, Long.parseLong(digits), line, col));
            } else {
                out.add(punct(c, col));
            }
        }
    }

    private Token punct(char c, int col) {
        char n = pos + 1 < src.length() ? src.charAt(pos + 1) : '\0';
        Token.Kind kind;
        int width = 1;
        switch (c) {
            case '(' -> kind = Token.Kind.LPAREN;
            case ')' -> kind = Token.Kind.RPAREN;
            case '{' -> kind = Token.Kind.LBRACE;
            case '}' -> kind = Token.Kind.RBRACE;
            case '[' -> kind = Token.Kind.LBRACKET;
            case ']' -> kind = Token.Kind.RBRACKET;
            case ',' -> kind = Token.Kind.COMMA;
            case ';' -> kind = Token.Kind.SEMI;
            case ':' -> kind = Token.Kind.COLON;
            case '+' -> kind = Token.Kind.PLUS;
            case '-' -> kind = Token.Kind.MINUS;
            case '*' -> kind = Token.Kind.STAR;
            case '/' -> kind = Token.Kind.SLASH;
            case '%' -> kind = Token.Kind.PERCENT;
            case '=' -> {
                if (n == '=') { kind = Token.Kind.EQ; width = 2; } else { kind = Token.Kind.ASSIGN; }
            }
            case '!' -> {
                if (n == '=') { kind = Token.Kind.NE; width = 2; } else { kind = Token.Kind.NOT; }
            }
            case '<' -> {
                if (n == '=') { kind = Token.Kind.LE; width = 2; } else { kind = Token.Kind.LT; }
            }
            case '>' -> {
                if (n == '=') { kind = Token.Kind.GE; width = 2; } else { kind = Token.Kind.GT; }
            }
            case '&' -> {
                if (n != '&') throw new CompileError(line, col, "expected '&&'");
                kind = Token.Kind.AND;
                width = 2;
            }
            case '|' -> {
                if (n != '|') throw new CompileError(line, col, "expected '||'");
                kind = Token.Kind.OR;
                width = 2;
            }
            default -> throw new CompileError(line, col, "unexpected character '" + c + "'");
        }
        String text = src.substring(pos, pos + width);
        pos += width;
        return new Token(kind, text, 0, line, col);
    }

    private void skipSpaceAndComments() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\n') {
                pos++;
                line++;
                lineStart = pos;
            } else if (c == ' ' || c == '\t' || c == '\r') {
                pos++;
            } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                while (pos < src.length() && src.charAt(pos) != '\n') {
                    pos++;
                }
            } else {
                return;
            }
        }
    }
}
