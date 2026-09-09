<<<<<<< HEAD
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
=======
import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;

public class Translator {
    File file = new File ("Example.java");

public void readFile() throws FileNotFoundException {
    Scanner scanner = new Scanner (file);
    while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        System.out.println(line);
        
    }
>>>>>>> 85306c8bace4f60547470739f12cffac7fdd29bf

public class Translator {
    private final Set<String> stringVariables = new HashSet<>();
    private final Set<String> scannerVariables = new HashSet<>();
    private final Set<String> stringArrays = new HashSet<>();
    private final Set<String> arrayVariables = new HashSet<>();
    private boolean inBlockComment;
    private boolean usesArrays;
    private String returnType;
    private boolean usesStringReturnStream;
    private final List<String> methodDeclarations = new ArrayList<>();
    private final Set<String> methodNames = new HashSet<>();
    private static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_]*";
    private static final Pattern METHOD = Pattern.compile(
            "public\\s+static\\s+(void|int|double|boolean|String)\\s+(" + IDENTIFIER + ")\\s*\\(([^()]*)\\)\\s*\\{");
    private static final Pattern PARAMETER = Pattern.compile(
            "(int|double|boolean|String)\\s+(" + IDENTIFIER + ")");
    // Match literals/comments first so their contents are never rewritten.
    private static final Pattern ARRAY_LENGTH = Pattern.compile(
            "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|//.*|/\\*.*?(?:\\*/|$)"
                    + "|(?<![\\w$.])(" + IDENTIFIER + ")\\s*\\.\\s*length\\b(?!\\s*\\()");
    private static final Pattern ARRAY_ALLOCATION = Pattern.compile(
            "(int|double|String)\\s*\\[\\s*\\]\\s+(" + IDENTIFIER
                    + ")\\s*=\\s*new\\s+\\1\\s*\\[\\s*([^\\[\\];{}]+)\\s*\\]\\s*;");
    private static final Pattern INT_ARRAY_INITIALIZER = Pattern.compile(
            "int\\s*\\[\\s*\\]\\s+(" + IDENTIFIER + ")\\s*=\\s*\\{([^{}]*)\\}\\s*;");
    private static final Pattern SCANNER_DECLARATION = Pattern.compile(
            "Scanner\\s+(" + IDENTIFIER + ")\\s*=\\s*new\\s+Scanner\\s*\\(\\s*System\\.in\\s*\\)\\s*;");
    private static final Pattern STRING_INPUT = Pattern.compile(
            "String\\s+(" + IDENTIFIER + ")\\s*=\\s*(" + IDENTIFIER + ")\\.nextLine\\s*\\(\\s*\\)\\s*;");
    private static final Pattern NUMBER_INPUT = Pattern.compile(
            "(int|double)\\s+(" + IDENTIFIER + ")\\s*=\\s*(Integer|Double)\\.valueOf\\s*\\(\\s*("
                    + IDENTIFIER + ")\\.nextLine\\s*\\(\\s*\\)\\s*\\)\\s*;");
    public String translateLine(String line) {
        if (line == null) {
            return "";
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        trimmed = convertArrayLengths(trimmed);

        if (trimmed.matches("import\\s+java\\.util\\.Scanner\\s*;")) {
            return "";
        }
        String scannerTranslation = translateScannerLine(trimmed);
        if (scannerTranslation != null) {
            return scannerTranslation;
        }
        String arrayTranslation = translateArrayLine(trimmed);
        if (arrayTranslation != null) {
            return arrayTranslation;
        }

        if (trimmed.startsWith("public class ")) {
            return "";
        }

        if (trimmed.matches("public\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*\\[\\s*\\]\\s+"
                + IDENTIFIER + "\\s*\\)\\s*\\{")) {
            clearLocalVariables();
            returnType = "int";
            return "int main() {";
        }

        Matcher method = METHOD.matcher(trimmed);
        if (method.matches()) {
            return translateMethod(method);
        }
        if (trimmed.matches("public\\s+static\\b.*")) {
            throw new IllegalArgumentException("Unsupported method declaration: " + trimmed
                    + ". Use a single-line signature with simple parameters and an opening brace.");
        }
        if (trimmed.matches("return\\s*;")) {
            return "    return;";
        }
        if (trimmed.startsWith("return ") && trimmed.endsWith(";")) {
            String expression = trimmed.substring(7, trimmed.length() - 1).trim();
            if ("String".equals(returnType)
                    && splitAddition(unwrapParentheses(expression)).size() > 1) {
                usesStringReturnStream = true;
                // Use the same concatenation rules as print, but collect the text
                // in memory instead of displaying it on the console.
                String temporary = "translatedReturnText";
                while (expression.contains(temporary)) temporary += "_";
                return String.join(System.lineSeparator(),
                        "    {", "        std::ostringstream " + temporary + ";",
                        "        " + temporary + " << std::boolalpha << " + convertPrintExpression(expression) + ";",
                        "        return " + temporary + ".str();", "    }");
            }
            return "    return " + expression + ";";
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

    private static String cppType(String type) {
        if (type.equals("String")) return "std::string";
        if (type.equals("boolean")) return "bool";
        return type;
    }

    private void clearLocalVariables() {
        stringVariables.clear();
        scannerVariables.clear();
        stringArrays.clear();
        arrayVariables.clear();
    }

    private String translateMethod(Matcher method) {
        String name = method.group(2);
        if (!methodNames.add(name)) {
            throw new IllegalArgumentException("Method overloading is not supported: " + name);
        }
        clearLocalVariables();
        returnType = method.group(1);
        List<String> parameters = new ArrayList<>();
        if (!method.group(3).trim().isEmpty()) {
            for (String text : method.group(3).split(",", -1)) {
                Matcher parameter = PARAMETER.matcher(text.trim());
                if (!parameter.matches()) {
                    throw new IllegalArgumentException("Unsupported parameter: " + text.trim());
                }
                String type = parameter.group(1);
                String variable = parameter.group(2);
                if (type.equals("String")) stringVariables.add(variable);
                parameters.add(cppType(type) + " " + variable);
            }
        }
        String signature = cppType(returnType) + " " + name + "(" + String.join(", ", parameters) + ")";
        // C++ needs a declaration before a call, even if the definition is later.
        methodDeclarations.add(signature + ";");
        return signature + " {";
    }

    private String convertArrayLengths(String line) {
        int start = 0;
        if (inBlockComment) {
            int end = line.indexOf("*/");
            if (end < 0) return line;
            start = end + 2;
            inBlockComment = false;
        }
        StringBuilder result = new StringBuilder(line.substring(0, start));
        Matcher matcher = ARRAY_LENGTH.matcher(line);
        matcher.region(start, line.length());
        int copied = start;
        while (matcher.find()) {
            result.append(line, copied, matcher.start());
            String name = matcher.group(1);
            int previous = matcher.start() - 1;
            while (previous >= 0 && Character.isWhitespace(line.charAt(previous))) previous--;
            boolean memberAccess = previous >= 0 && line.charAt(previous) == '.';
            if (name != null && arrayVariables.contains(name) && !memberAccess) {
                result.append(name).append(".size()");
            } else {
                result.append(matcher.group());
            }
            if (matcher.group().startsWith("/*") && !matcher.group().endsWith("*/")) {
                inBlockComment = true;
            }
            copied = matcher.end();
        }
        return result.append(line.substring(copied)).toString();
    }

    // Vector construction supplies zeroes for numeric elements. String elements
    // start as empty strings (Java String arrays instead start with null).
    private String translateArrayLine(String line) {
        Matcher allocation = ARRAY_ALLOCATION.matcher(line);
        if (allocation.matches()) {
            String type = allocation.group(1);
            String name = allocation.group(2);
            arrayVariables.add(name);
            usesArrays = true;
            if (type.equals("String")) stringArrays.add(name);
            else stringArrays.remove(name);
            String cppType = type.equals("String") ? "std::string" : type;
            return "    std::vector<" + cppType + "> " + name + "(" + allocation.group(3).trim() + ");";
        }
        Matcher initializer = INT_ARRAY_INITIALIZER.matcher(line);
        if (initializer.matches()) {
            arrayVariables.add(initializer.group(1));
            usesArrays = true;
            stringArrays.remove(initializer.group(1));
            return "    std::vector<int> " + initializer.group(1) + " = {" + initializer.group(2) + "};";
        }
        // Element access and assignment already share C++ syntax: numbers[i] = 10;
        return null;
    }

    // A null result means this is not one of our supported Scanner statements.
    private String translateScannerLine(String line) {
        Matcher declaration = SCANNER_DECLARATION.matcher(line);
        if (declaration.matches()) {
            scannerVariables.add(declaration.group(1));
            return ""; // C++ reads directly from std::cin; no Scanner object is needed.
        }

        Matcher text = STRING_INPUT.matcher(line);
        if (text.matches() && scannerVariables.contains(text.group(2))) {
            String name = text.group(1);
            stringVariables.add(name);
            return "    std::string " + name + ";" + System.lineSeparator()
                    + "    std::getline(std::cin, " + name + ");";
        }

        Matcher number = NUMBER_INPUT.matcher(line);
        if (number.matches() && scannerVariables.contains(number.group(4))) {
            String type = number.group(1);
            String wrapper = number.group(3);
            if (!(type.equals("int") && wrapper.equals("Integer"))
                    && !(type.equals("double") && wrapper.equals("Double"))) {
                return null;
            }
            String name = number.group(2);
            stringVariables.remove(name);
            String inputLine = name + "InputLine";
            String conversion = type.equals("int") ? "std::stoi" : "std::stod";
            // Read the whole line before converting, so the next text read does not
            // accidentally consume a newline left over by numeric stream extraction.
            // The block keeps the temporary string local to this one input operation.
            return String.join(System.lineSeparator(),
                    "    " + type + " " + name + ";",
                    "    {",
                    "        std::string " + inputLine + ";",
                    "        std::getline(std::cin, " + inputLine + ");",
                    "        " + name + " = " + conversion + "(" + inputLine + ");",
                    "    }");
        }
        return null;
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
        Matcher element = Pattern.compile("(" + IDENTIFIER + ")\\s*\\[[^\\[\\]]+\\]").matcher(value);
        return value.startsWith("\"") || stringVariables.contains(value)
                || (element.matches() && stringArrays.contains(element.group(1)));
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
        scannerVariables.clear();
        stringArrays.clear();
        arrayVariables.clear();
        inBlockComment = false;
        usesArrays = false;
        returnType = null;
        usesStringReturnStream = false;
        methodDeclarations.clear();
        methodNames.clear();
        String sanitized = javaCode
                .replaceFirst("(?s)public\\s+class\\s+\\w+\\s*\\{\\s*", "")
                .replaceFirst("(?s)\\s*\\}\s*$", "");

        List<String> translatedLines = new ArrayList<>();

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

        String body = String.join(System.lineSeparator(), translatedLines);
        List<String> headers = new ArrayList<>();
        if (body.contains("std::cin") || body.contains("std::cout")) {
            headers.add("#include <iostream>");
        }
        if (body.contains("std::string") || body.contains("std::stoi") || body.contains("std::stod")) {
            headers.add("#include <string>");
        }
        if (usesArrays) {
            headers.add("#include <vector>");
        }
        if (usesStringReturnStream) {
            headers.add("#include <sstream>");
        }
        headers.add("");
        headers.addAll(methodDeclarations);
        if (!methodDeclarations.isEmpty()) headers.add("");
        headers.add(body);
        return String.join(System.lineSeparator(), headers);
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
}
