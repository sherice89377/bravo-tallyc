package tally.test;

public final class CompilerTest {
    private CompilerTest() {}

    public static void testStraightLine() {
        Check.sameAsInterpreter("fn main() { var a = 6; var b = 7; print(a * b); print(a - b); print(a < b); }", "");
    }

    public static void testInput() {
        Check.sameAsInterpreter("fn main() { var a = read(); var b = read(); print(a + b); print(a * b); }", "20 22");
    }

    public static void testIfElse() {
        Check.sameAsInterpreter("fn main() { var a = read(); if (a > 10) { print(1); } else if (a > 5) { print(2); } else { print(3); } }", "7");
    }

    public static void testSimpleCall() {
        Check.sameAsInterpreter("fn answer(): int { return 2 * 21; } fn main() { print(answer()); }", "");
    }

    public static void testRuntimeErrorAfterOutput() {
        Check.sameAsInterpreter("fn main() { var z = read(); print(5); print(10 / z); }", "0");
    }

    public static void testArrayStore() {
        Check.sameAsInterpreter("fn main() { var a = new int[4]; a[2] = 5; a[3] = a[2] * 2; print(a[3]); print(len(a)); }", "");
    }
}
