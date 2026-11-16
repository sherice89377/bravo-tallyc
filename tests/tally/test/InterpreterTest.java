package tally.test;

public final class InterpreterTest {
    private InterpreterTest() {}

    public static void testDivisionTruncates() {
        Check.equal("-3\n-1\n-3\n1\nstatus 0",
                Check.interpret("fn main() { print(-7 / 2); print(-7 % 2); print(7 / -2); print(7 % -2); }", ""));
    }

    public static void testWrapAround() {
        Check.equal("-2147483648\n-2147483648\n0\nstatus 0", Check.interpret(
                "fn main() { print(2147483647 + 1); print((-2147483647 - 1) / -1); print((-2147483647 - 1) % -1); }", ""));
    }

    public static void testShortCircuit() {
        Check.equal("0\n1\nstatus 0", Check.interpret("fn main() { var a = new int[1]; print(0 && a[5]); print(1 || a[5]); }", ""));
    }

    public static void testRuntimeErrors() {
        Check.equal("1\nruntime error: division by zero\nstatus 1", Check.interpret("fn main() { print(1); print(1 / (1 - 1)); }", ""));
        Check.equal("runtime error: index out of bounds\nstatus 1", Check.interpret("fn main() { var a = new int[2]; a[2] = 1; }", ""));
        Check.equal("runtime error: negative array size\nstatus 1", Check.interpret("fn main() { var a = new int[-1]; }", ""));
    }

    public static void testRead() {
        Check.equal("5\n-2\n0\nstatus 0", Check.interpret("fn main() { print(read()); print(read()); print(read()); }", " 5\n\t-2 "));
        Check.equal("runtime error: invalid input\nstatus 1", Check.interpret("fn main() { print(read()); }", "1x"));
    }

    public static void testArraysAreShared() {
        Check.equal("9\nstatus 0", Check.interpret("fn set(a: int[]) { a[0] = 9; } fn main() { var a = new int[1]; set(a); print(a[0]); }", ""));
    }
}
