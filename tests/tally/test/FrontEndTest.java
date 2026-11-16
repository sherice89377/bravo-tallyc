package tally.test;

import tally.front.Checker;
import tally.front.Parser;

public final class FrontEndTest {
    private FrontEndTest() {}

    private static void check(String src) {
        Checker.check(Parser.parse(src));
    }

    public static void testAcceptsAWellFormedProgram() {
        check("fn sq(x: int): int { return x * x; } fn main() { var a = new int[3]; a[1] = sq(4); print(a[1] + len(a)); }");
    }

    public static void testPrecedence() {
        Check.equal("7\nstatus 0", Check.interpret("fn main() { print(1 + 2 * 3); }", ""));
        Check.equal("1\nstatus 0", Check.interpret("fn main() { print(1 < 2 == 1); }", ""));
    }

    public static void testRejectsShadowing() {
        Check.fails(() -> check("fn main() { var x = 1; if (x) { var x = 2; } }"), "already declared");
    }

    public static void testSiblingBlocksMayReuseANames() {
        check("fn main() { if (1) { var x = 1; print(x); } else { var x = 2; print(x); } }");
    }

    public static void testRejectsTypeErrors() {
        Check.fails(() -> check("fn main() { var a = new int[2]; print(a); }"), "must be int");
        Check.fails(() -> check("fn f(): int { return 1; } fn main() { var a = new int[1]; a = f(); }"), "cannot assign");
        Check.fails(() -> check("fn g() { } fn main() { var x = g(); }"), "returns nothing");
    }

    public static void testMainRules() {
        Check.fails(() -> check("fn f() { }"), "no function 'main'");
        Check.fails(() -> check("fn main(x: int) { }"), "'main' must take no parameters");
    }

    public static void testLiteralRange() {
        Check.fails(() -> check("fn main() { print(2147483648); }"), "out of range");
    }
}
