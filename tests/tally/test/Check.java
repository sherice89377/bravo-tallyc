package tally.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import tally.backend.Compiler;
import tally.front.Ast;
import tally.front.Checker;
import tally.front.Parser;
import tally.interp.Interpreter;
import tally.vm.Assembler;
import tally.vm.Vm;

/** Assertions and helpers shared by the suites. */
final class Check {
    private Check() {}

    static void equal(Object want, Object got) {
        if (want == null ? got != null : !want.equals(got)) {
            throw new AssertionError("expected <" + want + "> but got <" + got + ">");
        }
    }

    static void fails(Runnable r, String fragment) {
        try {
            r.run();
        } catch (RuntimeException e) {
            if (e.getMessage() == null || !e.getMessage().contains(fragment)) {
                throw new AssertionError("expected an error containing <" + fragment + "> but got <" + e.getMessage() + ">");
            }
            return;
        }
        throw new AssertionError("expected an error containing <" + fragment + ">");
    }

    /** stdout and exit status of the interpreter. */
    static String interpret(String src, String input) {
        Ast.Program p = Parser.parse(src);
        Checker.check(p);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        int status = new Interpreter(p, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(bo, true, StandardCharsets.UTF_8)).run();
        return bo.toString(StandardCharsets.UTF_8) + "status " + status;
    }

    /** stdout and exit status of the compiled program on the VM. */
    static String execute(String src, int level, String input) {
        return runAsm(Compiler.compile(src, level, null), input);
    }

    static String runAsm(String asm, String input) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        Vm vm = new Vm(Assembler.assemble(asm), new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(bo, true, StandardCharsets.UTF_8), 100_000_000L);
        int status = vm.run();
        return bo.toString(StandardCharsets.UTF_8) + "status " + status;
    }

    /** The compiled program, at both levels, prints what the interpreter prints. */
    static void sameAsInterpreter(String src, String input) {
        String want = interpret(src, input);
        equal(want, execute(src, 0, input));
        equal(want, execute(src, 2, input));
    }
}
