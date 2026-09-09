import java.util.ArrayList;
import java.util.List;

/** Final whitespace-only pass for the small C++ subset we generate. */
public final class CppFormatter {
    private CppFormatter() {
    }

    public static String format(String code) {
        List<String> lines = new ArrayList<>();
        int depth = 0;
        boolean blockComment = false;
        for (String original : code.split("\\r?\\n", -1)) {
            String line = original.stripLeading();
            int closingPrefix = 0;
            int braceChange = 0;
            boolean atStart = true;
            char quote = 0;
            // Includes stay at column zero and do not contribute braces.
            if (!blockComment && line.startsWith("#")) {
                lines.add(line);
                continue;
            }
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';
                if (blockComment) {
                    if (c == '*' && next == '/') {
                        blockComment = false;
                        i++;
                    }
                    continue;
                }
                if (quote != 0) {
                    if (c == '\\') i++; // Skip an escaped quote or backslash.
                    else if (c == quote) quote = 0;
                    continue;
                }
                if (c == '/' && next == '/') break;
                if (c == '/' && next == '*') {
                    blockComment = true;
                    i++;
                    continue;
                }
                if (Character.isWhitespace(c)) continue;
                if (c == '}') {
                    braceChange--;
                    if (atStart) closingPrefix++;
                } else {
                    atStart = false;
                    if (c == '{') braceChange++;
                    else if (c == '"' || c == '\'') quote = c;
                }
            }
            // A line such as "} else {" closes the previous block before it opens
            // the next one, so it lines up with the corresponding if statement.
            int indentation = Math.max(0, depth - closingPrefix);
            lines.add(line.isEmpty() ? "" : "    ".repeat(indentation) + line);
            depth = Math.max(0, depth + braceChange);
        }
        return String.join(System.lineSeparator(), lines);
    }
}
