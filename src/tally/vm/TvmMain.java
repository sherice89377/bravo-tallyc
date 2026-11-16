package tally.vm;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The tvm command: {@code tvm run FILE [--max-cycles N] [--stats]}. Exit status 0, 1 after a
 * runtime error, 2 for a malformed assembly file, 64 for a usage error. {@code --stats} prints the
 * cycle count to stderr.
 */
public final class TvmMain {
    private TvmMain() {}

    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    static int run(String[] args) throws IOException {
        if (args.length < 2 || !args[0].equals("run")) {
            return usage();
        }
        long maxCycles = Vm.DEFAULT_MAX_CYCLES;
        boolean stats = false;
        for (int k = 2; k < args.length; k++) {
            switch (args[k]) {
                case "--max-cycles" -> {
                    if (++k >= args.length) return usage();
                    maxCycles = Long.parseLong(args[k]);
                }
                case "--stats" -> stats = true;
                default -> {
                    return usage();
                }
            }
        }
        Program p;
        try {
            p = Assembler.assemble(Files.readString(Path.of(args[1]), StandardCharsets.UTF_8));
        } catch (Assembler.AsmError e) {
            System.err.println(args[1] + ": " + e.getMessage());
            return 2;
        }
        PrintStream out = new PrintStream(new BufferedOutputStream(System.out, 1 << 16), false, StandardCharsets.UTF_8);
        Vm vm = new Vm(p, System.in, out, maxCycles);
        int status = vm.run();
        if (stats) System.err.println("cycles: " + vm.cycles);
        return status;
    }

    private static int usage() {
        System.err.println("usage: tvm run FILE [--max-cycles N] [--stats]");
        return 64;
    }
}
