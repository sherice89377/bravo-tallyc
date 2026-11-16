package tally;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import tally.backend.Compiler;
import tally.front.Ast;
import tally.front.Checker;
import tally.front.CompileError;
import tally.front.Parser;
import tally.interp.Interpreter;

/**
 * The tallyc command.
 *
 * <pre>
 *   tallyc run FILE                      interpret FILE (the reference semantics)
 *   tallyc build FILE -o OUT [-O0|-O2]   compile FILE to TVM assembly (default -O2)
 *   tallyc build FILE --dump-ir          also print the optimised IR and the allocation to stderr
 *   tallyc check FILE                    parse and type-check only
 * </pre>
 *
 * Exit status: what the program's run gives for {@code run} (0, or 1 after a runtime error),
 * 2 for a compile error, 64 for a usage error.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    static int run(String[] args) throws IOException {
        if (args.length < 2) {
            return usage();
        }
        String cmd = args[0];
        String file = args[1];
        String source = Files.readString(Path.of(file), StandardCharsets.UTF_8);
        try {
            switch (cmd) {
                case "run" -> {
                    Ast.Program p = Parser.parse(source);
                    Checker.check(p);
                    PrintStream out = new PrintStream(new BufferedOutputStream(System.out, 1 << 16), false, StandardCharsets.UTF_8);
                    return new Interpreter(p, System.in, out).run();
                }
                case "check" -> {
                    Checker.check(Parser.parse(source));
                    return 0;
                }
                case "build" -> {
                    String outFile = null;
                    int level = 2;
                    boolean dump = false;
                    for (int k = 2; k < args.length; k++) {
                        switch (args[k]) {
                            case "-o" -> {
                                if (++k >= args.length) return usage();
                                outFile = args[k];
                            }
                            case "-O0" -> level = 0;
                            case "-O2" -> level = 2;
                            case "--dump-ir" -> dump = true;
                            default -> {
                                return usage();
                            }
                        }
                    }
                    StringBuilder ir = dump ? new StringBuilder() : null;
                    String asm = Compiler.compile(source, level, ir);
                    if (ir != null) System.err.print(ir);
                    if (outFile != null) {
                        Files.writeString(Path.of(outFile), asm, StandardCharsets.UTF_8);
                    } else {
                        System.out.print(asm);
                    }
                    return 0;
                }
                default -> {
                    return usage();
                }
            }
        } catch (CompileError e) {
            System.err.println(e.render(file));
            return 2;
        }
    }

    private static int usage() {
        System.err.println("usage: tallyc run FILE | tallyc check FILE | tallyc build FILE [-o OUT] [-O0|-O2] [--dump-ir]");
        return 64;
    }
}
