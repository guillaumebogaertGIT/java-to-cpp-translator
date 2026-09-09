import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class Translator {
    private final Set<String> stringVariables = new HashSet<>();
    public String translateLine(String line) {
        if (line == null) {
            return "";
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        if (trimmed.startsWith("public class ")) {
            return "";
        }

        if (trimmed.startsWith("public static void main")) {
            return "int main() {";
        }

        if (trimmed.startsWith("public static ")) {
            return "";
        }

        if (trimmed.equals("}")) {
            return "}";
        }

        if (trimmed.startsWith("if (") || trimmed.startsWith("else if (") || trimmed.startsWith("for (") || trimmed.startsWith("while (")) {
            return "    " + trimmed + (trimmed.endsWith("{") ? "" : " {");
        }

        if (trimmed.startsWith("else")) {
            return "    " + (trimmed.endsWith("{") ? trimmed : "else {");
        }

        if (trimmed.startsWith("System.out.println(")) {
            String content = trimmed.substring("System.out.println(".length(), trimmed.length() - 2);
            return content.trim().isEmpty() ? "    std::cout << std::endl;"
                    : "    std::cout << " + convertPrintExpression(content) + " << std::endl;";
        }

        if (trimmed.startsWith("System.out.print(")) {
            String content = trimmed.substring("System.out.print(".length(), trimmed.length() - 2);
            return "    std::cout << " + convertPrintExpression(content) + ";";
        }

        if (trimmed.matches("(int|double|boolean|String)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*=.*;")) {
            return "    " + convertDeclaration(trimmed);
        }

        if (trimmed.matches("(int|double|boolean|String)\\s+[A-Za-z_][A-Za-z0-9_]*;")) {
            return "    " + convertDeclaration(trimmed);
        }

        if (trimmed.matches("[A-Za-z_][A-Za-z0-9_]*\\s*=.*;")) {
            return "    " + convertAssignment(trimmed);
        }

        return "    " + trimmed;
    }

    private String convertDeclaration(String declaration) {
        String[] parts = declaration.split("\\s+", 2);
        String name = parts[1].split("\\s*=|;", 2)[0].trim();
        if (parts[0].equals("String")) {
            stringVariables.add(name);
        } else {
            stringVariables.remove(name);
        }
        String converted = declaration;
        converted = converted.replace("String ", "std::string ");
        converted = converted.replace("boolean ", "bool ");
        return converted;
    }

    private static String convertAssignment(String assignment) {
        return assignment;
    }

    // Only split addition outside strings, character literals, and parentheses.
    // Java evaluates + left to right: 2 + 3 + " items" starts with numeric 5.
    private String convertPrintExpression(String expression) {
        String value = unwrapParentheses(expression.trim());
        List<String> terms = splitAddition(value);
        int firstString = -1;
        for (int i = 0; i < terms.size(); i++) {
            if (isStringExpression(terms.get(i))) {
                firstString = i;
                break;
            }
        }
        if (firstString < 0) {
            return streamOperand(expression.trim());
        }

        List<String> output = new ArrayList<>();
        if (firstString > 0) {
            output.add(streamOperand(String.join(" + ", terms.subList(0, firstString))));
        }
        for (int i = firstString; i < terms.size(); i++) {
            String term = terms.get(i);
            String unwrapped = unwrapParentheses(term);
            if (splitAddition(unwrapped).size() > 1 && isStringExpression(unwrapped)) {
                output.add(convertPrintExpression(unwrapped));
            } else {
                output.add(streamOperand(term));
            }
        }
        return String.join(" << ", output);
    }

    private boolean isStringExpression(String expression) {
        String value = unwrapParentheses(expression.trim());
        List<String> terms = splitAddition(value);
        if (terms.size() > 1) {
            for (String term : terms) {
                if (isStringExpression(term)) return true;
            }
            return false;
        }
        return value.startsWith("\"") || stringVariables.contains(value);
    }

    private static String streamOperand(String value) {
        // Parentheses also protect comparisons and shifts from C++ << precedence.
        if (value.matches("[A-Za-z_][A-Za-z0-9_]*|[0-9]+(?:\\.[0-9]+)?")
                || value.matches("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)'")) {
            return value;
        }
        return "(" + value + ")";
    }

    private static List<String> splitAddition(String value) {
        List<String> terms = new ArrayList<>();
        int depth = 0;
        int start = 0;
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '\"' || c == '\'') quote = c;
            else if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            else if (c == '+' && depth == 0) {
                if (i + 1 < value.length() && value.charAt(i + 1) == '+') {
                    i++; // Preserve increment operators.
                    continue;
                }
                String before = value.substring(start, i).trim();
                if (before.isEmpty() || "+-*/%<>=!&|?:".indexOf(before.charAt(before.length() - 1)) >= 0) {
                    continue; // Unary plus is part of its operand.
                }
                terms.add(before);
                start = i + 1;
            }
        }
        terms.add(value.substring(start).trim());
        return terms;
    }

    private static String unwrapParentheses(String value) {
        while (value.startsWith("(") && value.endsWith(")")) {
            int depth = 0;
            char quote = 0;
            boolean wrapsAll = true;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (quote != 0) {
                    if (c == '\\') i++;
                    else if (c == quote) quote = 0;
                } else if (c == '\"' || c == '\'') quote = c;
                else if (c == '(') depth++;
                else if (c == ')' && --depth == 0 && i < value.length() - 1) {
                    wrapsAll = false;
                    break;
                }
            }
            if (!wrapsAll) break;
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    public String translate(String javaCode) {
        stringVariables.clear();
        String sanitized = javaCode
                .replaceFirst("(?s)public\\s+class\\s+\\w+\\s*\\{\\s*", "")
                .replaceFirst("(?s)\\s*\\}\s*$", "");

        List<String> translatedLines = new ArrayList<>();
        translatedLines.add("#include <iostream>");
        translatedLines.add("#include <string>");
        translatedLines.add("");

        for (String rawLine : sanitized.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            String translated = translateLine(line);
            if (!translated.isEmpty()) {
                translatedLines.add(translated);
            }
        }

        return String.join(System.lineSeparator(), translatedLines);
    }

    public void translateFile(String inputPath, String outputPath) throws IOException {
        Path in = Paths.get(inputPath);
        String javaCode = Files.readString(in);
        String translated = translate(javaCode);
        Path out = Paths.get(outputPath);
        Files.createDirectories(out.getParent() == null ? Paths.get(".") : out.getParent());
        Files.writeString(out, translated);
    }
}
