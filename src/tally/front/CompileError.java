package tally.front;

/** A diagnostic for a program that is not valid Tally. Printed as {@code file:line:column: message}. */
public final class CompileError extends RuntimeException {
    public final int line;
    public final int column;

    public CompileError(int line, int column, String message) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public String render(String file) {
        return file + ":" + line + ":" + column + ": " + getMessage();
    }
}
