package tally.test;

public final class VmTest {
    private VmTest() {}

    public static void testArithmetic() {
        Check.equal("-3\n-1\nstatus 0", Check.runAsm(
                "main:\n enter 0\n li r1, -7\n li r2, 2\n div r3, r1, r2\n print r3\n rem r3, r1, r2\n print r3\n leave\n ret\n", ""));
    }

    public static void testCallAndFrame() {
        Check.equal("42\nstatus 0", Check.runAsm(
                "f:\n enter 0\n ld r0, [fp+2]\n leave\n ret\nmain:\n enter 0\n li r1, 42\n push r1\n call f\n addsp 1\n print r0\n leave\n ret\n", ""));
    }

    public static void testArrays() {
        Check.equal("3\n7\nruntime error: index out of bounds\nstatus 1", Check.runAsm(
                "main:\n enter 0\n li r1, 3\n newarr r2, r1\n alen r3, r2\n print r3\n li r4, 7\n li r5, 2\n astore r2, r5, r4\n"
                        + " aload r6, r2, r5\n print r6\n aload r6, r2, r1\n leave\n ret\n", ""));
    }

    public static void testBadHandle() {
        Check.equal("runtime error: invalid array reference\nstatus 1", Check.runAsm(
                "main:\n enter 0\n li r1, 5\n alen r2, r1\n leave\n ret\n", ""));
    }

    public static void testAssemblerErrors() {
        Check.fails(() -> Check.runAsm("main:\n jmp nowhere\n", ""), "unknown label");
        Check.fails(() -> Check.runAsm("main:\n add r1, r2\n", ""), "takes 3 operands");
    }
}
