package tally.vm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The program's input for {@code read()}: integers separated by spaces, tabs and line breaks.
 * After the last 1, every read gives 0. A word that is not an integer in int range is an error.
 */
public final class Input {
    private final InputStream in;
    private String text;
    private int pos;

    public Input(InputStream in) {
        this.in = in;
    }

    public int next() {
        if (text == null) {
            try {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                text = "";
            }
        }
        while (pos < text.length() && isSpace(text.charAt(pos))) pos++;
        if (pos >= text.length()) return 0;
        int start = pos;
        while (pos < text.length() && !isSpace(text.charAt(pos))) pos++;
        String word = text.substring(start, pos);
        if (!word.matches("-?[0-9]{1,10}")) throw new Vm.Trap("invalid input");
        long v = Long.parseLong(word);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) throw new Vm.Trap("invalid input");
        return (int) v;
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }
}
