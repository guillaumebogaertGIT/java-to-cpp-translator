public class TranslatorTest {
    private static int checks = 0;

    private static void check(Translator translator, String javaLine, String expected) {
        String actual = translator.translateLine(javaLine).trim();
        if (!actual.equals(expected)) {
            throw new AssertionError(javaLine + "\nExpected: " + expected + "\nActual: " + actual);
        }
        checks++;
    }

    public static void main(String[] args) {
        Translator translator = new Translator();
        check(translator, "System.out.println(\"Total: \" + total);",
                "std::cout << \"Total: \" << total << std::endl;");
        check(translator, "System.out.print(\"Total: \" + total);",
                "std::cout << \"Total: \" << total;");
        check(translator, "System.out.println(2 + 3);",
                "std::cout << (2 + 3) << std::endl;");
        check(translator, "System.out.println(2 + 3 + \" items\");",
                "std::cout << (2 + 3) << \" items\" << std::endl;");
        check(translator, "System.out.println(\"Total: \" + 2 + 3);",
                "std::cout << \"Total: \" << 2 << 3 << std::endl;");
        check(translator, "System.out.println(\"Total: \" + (2 + 3));",
                "std::cout << \"Total: \" << ((2 + 3)) << std::endl;");
        check(translator, "System.out.println(\"a + \\\"b\\\"\" + total);",
                "std::cout << \"a + \\\"b\\\"\" << total << std::endl;");
        check(translator, "System.out.println(\"Nested: \" + (\"value=\" + total));",
                "std::cout << \"Nested: \" << \"value=\" << total << std::endl;");
        translator.translateLine("String label = \"Total: \";");
        check(translator, "System.out.println(label + total);",
                "std::cout << label << total << std::endl;");
        check(translator, "System.out.println();", "std::cout << std::endl;");
        check(translator, "System.out.println(total++);", "std::cout << (total++) << std::endl;");
        check(translator, "System.out.println('a' + 1 + \" items\");",
                "std::cout << ('a' + 1) << \" items\" << std::endl;");
        translator.translate("public class Empty {\npublic static void main(String[] args) {\n}\n}");
        check(translator, "System.out.println(label + total);",
                "std::cout << (label + total) << std::endl;");
        testScanner();
        testArrays();
        testArrayLengths();
        testMethods();
        testIndentation();
        System.out.println("Passed " + checks + " translation checks.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static void testIndentation() {
        String newline = System.lineSeparator();
        String raw = String.join(newline, "int main() {", "for (int i = 0; i < 3; i++) {",
                "if (i > 1) {", "std::cout << i;", "} else {", "std::cout << 0;", "}", "}", "}");
        String expected = String.join(newline, "int main() {", "    for (int i = 0; i < 3; i++) {",
                "        if (i > 1) {", "            std::cout << i;", "        } else {",
                "            std::cout << 0;", "        }", "    }", "}");
        require(CppFormatter.format(raw).equals(expected), "Nested loops and inline else use four spaces");
        require(CppFormatter.format(expected).equals(expected), "Formatting is idempotent");
        String literals = String.join(newline, "void demo() {", "std::cout << \"\\\"{\";",
                "char brace = '}';", "// }", "/* {", "} */", "int value = 1;", "}");
        String formatted = CppFormatter.format(literals);
        require(formatted.contains(newline + "    int value = 1;" + newline + "}"),
                "Quoted braces, escaped quotes, and comments do not change indentation");
        require(CppFormatter.format("#include <vector>\n\nint main() {\nstd::vector<int> a = {1, 2};\na[0] = 3;\n}\n")
                .equals(String.join(newline, "#include <vector>", "", "int main() {",
                        "    std::vector<int> a = {1, 2};", "    a[0] = 3;", "}", "")),
                "Headers, blank lines, and initializers keep their structure");
        require(raw.replaceAll("\\s", "").equals(CppFormatter.format(raw).replaceAll("\\s", "")),
                "Formatting changes whitespace only");

        String code = new Translator().translate(String.join(newline,
                "public class Demo {", "public static void main(String[] args) {",
                "int[] numbers = {1, 2};", "for (int i = 0; i < numbers.length; i++) {",
                "System.out.println(numbers[i]);", "}", "}", "}"));
        require(code.contains(String.join(newline, "    for (int i = 0; i < numbers.size(); i++) {",
                "        std::cout << (numbers[i]) << std::endl;", "    }", "}")),
                "Full translation applies formatting after length and print conversion");
        String helpers = new Translator().translate(String.join(newline,
                "public class Demo {", "public static void main(String[] args) {",
                "Scanner scanner = new Scanner(System.in);", "int age = Integer.valueOf(scanner.nextLine());", "}",
                "public static String greet(String name) {", "return \"Hello \" + name;", "}", "}"));
        require(helpers.contains("        std::getline(std::cin, ageInputLine);")
                && helpers.contains("        return translatedReturnText.str();")
                && helpers.contains(newline + "std::string greet(std::string name) {"),
                "Generated Scanner and string-return blocks are indented, with methods at column zero");
    }

    private static void testMethods() {
        Translator translator = new Translator();
        check(translator, "public static int square(int number) {", "int square(int number) {");
        check(translator, "return number * number;", "return number * number;");
        check(translator, "public static double average(double a, double b) {", "double average(double a, double b) {");
        check(translator, "return (a + b) / 2.0;", "return (a + b) / 2.0;");
        check(translator, "public static boolean enabled(boolean flag, int count) {", "bool enabled(bool flag, int count) {");
        check(translator, "return flag && count > 0;", "return flag && count > 0;");
        check(translator, "public static void announce(String text) {", "void announce(std::string text) {");
        check(translator, "System.out.println(text + 1);", "std::cout << text << 1 << std::endl;");
        check(translator, "return;", "return;");
        check(translator, "public static int answer() {", "int answer() {");
        check(translator, "return 42;", "return 42;");
        check(translator, "public static String greeting(String name) {", "std::string greeting(std::string name) {");
        check(translator, "return \"Hello \" + name;", String.join(System.lineSeparator(),
                "{", "        std::ostringstream translatedReturnText;",
                "        translatedReturnText << std::boolalpha << \"Hello \" << name;",
                "        return translatedReturnText.str();", "    }"));
        check(translator, "return name;", "return name;");
        check(translator, "return \"Hello\";", "return \"Hello\";");
        check(translator, "public static int reuse(int name) {", "int reuse(int name) {");
        check(translator, "System.out.println(name + 1);", "std::cout << (name + 1) << std::endl;");
        check(translator, "public static void mainHelper() {", "void mainHelper() {");
        check(translator, "square(5);", "square(5);");
        check(translator, "int result = square(5);", "int result = square(5);");

        String code = translator.translate(String.join("\n",
                "public class MethodExample {", "public static void main(String[] args) {",
                "System.out.println(square(5));", "System.out.println(greeting(\"Ada\"));", "}",
                "public static int square(int number) {", "return number * number;", "}",
                "public static String greeting(String name) {", "return \"Hello \" + name;", "}", "}"));
        require(code.indexOf("int square(int number);") < code.indexOf("int main()"),
                "Methods declared before main can be defined after it");
        require(code.contains("std::string greeting(std::string name);") && code.contains("#include <sstream>"),
                "String methods have prototypes and the needed stream header");
        require(code.contains("std::cout << (greeting(\"Ada\")) << std::endl;"), "Method calls work in print");
        String numeric = translator.translate(String.join("\n", "public class Numeric {",
                "public static int square(int number) {", "return number * number;", "}", "}"));
        require(!numeric.contains("#include <sstream>") && !numeric.contains("greeting"),
                "Method state resets and numeric methods do not need sstream");
        try {
            translator.translateLine("public static int square(double number) {");
            throw new AssertionError("Overloads must be rejected");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            translator.translateLine("public static int size(int[] numbers) {");
            throw new AssertionError("Array parameters are outside the supported subset");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static void testScanner() {
        Translator translator = new Translator();
        check(translator, "import java.util.Scanner;", "");
        check(translator, "Scanner scanner = new Scanner(System.in);", "");
        check(translator, "String name = scanner.nextLine();",
                "std::string name;" + System.lineSeparator() + "    std::getline(std::cin, name);");
        check(translator, "int age = Integer.valueOf(scanner.nextLine());",
                numericInput("int", "age", "stoi"));
        check(translator, "double value = Double.valueOf(scanner.nextLine());",
                numericInput("double", "value", "stod"));
        check(translator, "System.out.println(name + age);",
                "std::cout << name << age << std::endl;");
        check(translator, "Scanner keyboard = new Scanner ( System.in );", "");
        check(translator, "String other = keyboard.nextLine ( );",
                "std::string other;" + System.lineSeparator() + "    std::getline(std::cin, other);");

        String code = translator.translate(String.join("\n",
                "import java.util.Scanner;",
                "public class InputExample {",
                "public static void main(String[] args) {",
                "Scanner scanner = new Scanner(System.in);",
                "String name = scanner.nextLine();",
                "int age = Integer.valueOf(scanner.nextLine());",
                "double value = Double.valueOf(scanner.nextLine());",
                "String last = scanner.nextLine();",
                "System.out.println(name + age);",
                "}", "}"));
        require(code.startsWith("#include <iostream>" + System.lineSeparator() + "#include <string>"),
                "Input needs iostream and string headers");
        require(!code.contains("Scanner") && !code.contains("nextLine") && !code.contains("valueOf"),
                "Supported input must not leave Java Scanner code behind");
        require(code.contains("std::getline(std::cin, last);"), "Text after numbers still reads a full line");
        require(!code.contains("std::cin >>"), "All supported input must consume the whole line");

        String empty = translator.translate("public class Empty {\npublic static void main(String[] args) {\n}\n}");
        require(!empty.contains("#include"), "No headers needed for an empty program");
        check(translator, "String name = scanner.nextLine();", "std::string name = scanner.nextLine();");
        String print = translator.translate("System.out.println(5);");
        require(print.contains("#include <iostream>") && !print.contains("#include <string>"),
                "Printing a number only needs iostream");
        String text = translator.translate("String name = \"Ada\";");
        require(text.contains("#include <string>") && !text.contains("#include <iostream>"),
                "A string declaration only needs string");
    }

    private static String numericInput(String type, String name, String conversion) {
        return String.join(System.lineSeparator(),
                type + " " + name + ";",
                "    {",
                "        std::string " + name + "InputLine;",
                "        std::getline(std::cin, " + name + "InputLine);",
                "        " + name + " = std::" + conversion + "(" + name + "InputLine);",
                "    }");
    }

    private static void testArrayLengths() {
        Translator translator = new Translator();
        translator.translateLine("int[] numbers = {1, 2, 3};");
        translator.translateLine("double[] values = new double[10];");
        translator.translateLine("String[] names = new String[3];");
        check(translator, "System.out.println(numbers.length);", "std::cout << (numbers.size()) << std::endl;");
        check(translator, "System.out.print(\"Count: \" + names.length);",
                "std::cout << \"Count: \" << (names.size());");
        check(translator, "int count = numbers.length;", "int count = numbers.size();");
        check(translator, "count = numbers.length + values.length;", "count = numbers.size() + values.size();");
        check(translator, "numbers[0] = names.length;", "numbers[0] = names.size();");
        check(translator, "numbers[numbers.length - 1] = 10;", "numbers[numbers.size() - 1] = 10;");
        check(translator, "for (int i = 0; i < numbers.length; i++)",
                "for (int i = 0; i < numbers.size(); i++) {");
        check(translator, "while (count < values.length) {", "while (count < values.size()) {");
        check(translator, "if (names.length > 0) {", "if (names.size() > 0) {");
        check(translator, "count = (numbers . length * 2);", "count = (numbers.size() * 2);");
        check(translator, "int[] copy = new int[numbers.length];", "std::vector<int> copy(numbers.size());");
        check(translator, "int[] sizes = {numbers.length, names.length};",
                "std::vector<int> sizes = {numbers.size(), names.size()};");
        check(translator, "System.out.println(\"numbers.length\" + numbers.length);",
                "std::cout << \"numbers.length\" << (numbers.size()) << std::endl;");
        check(translator, "String label = \"\\\"numbers.length\\\"\";", "std::string label = \"\\\"numbers.length\\\"\";");
        check(translator, "count = numbers.length; // numbers.length", "count = numbers.size(); // numbers.length");
        check(translator, "/* numbers.length */ count = numbers.length;", "/* numbers.length */ count = numbers.size();");
        check(translator, "/* numbers.length", "/* numbers.length");
        check(translator, "numbers.length */ count = numbers.length;", "numbers.length */ count = numbers.size();");
        check(translator, "count = unknown.length;", "count = unknown.length;");
        check(translator, "count = label.length();", "count = label.length();");
        check(translator, "count = numbers.lengthExtra;", "count = numbers.lengthExtra;");
        check(translator, "count = object. numbers.length;", "count = object. numbers.length;");
        String code = translator.translate(String.join("\n",
                "public class LengthExample {", "public static void main(String[] args) {",
                "int[] numbers = {1, 2, 3};", "int count = numbers.length;",
                "for (int i = 0; i < numbers.length; i++) {",
                "System.out.println(numbers[i]);", "}", "}", "}"));
        require(code.contains("#include <vector>") && code.contains("i < numbers.size()")
                && code.contains("int count = numbers.size();") && !code.contains(".length"),
                "Full program rewrites lengths and keeps array headers");
        translator.translate("int count = 0;");
        check(translator, "count = numbers.length;", "count = numbers.length;");
    }

    private static void testArrays() {
        Translator translator = new Translator();
        check(translator, "int[] numbers = new int[5];", "std::vector<int> numbers(5);");
        check(translator, "double[] values = new double[10];", "std::vector<double> values(10);");
        check(translator, "String[] names = new String[3];", "std::vector<std::string> names(3);");
        check(translator, "int[] numbers = {1, 2, 3, 4};", "std::vector<int> numbers = {1, 2, 3, 4};");
        check(translator, "numbers[0] = 10;", "numbers[0] = 10;");
        check(translator, "System.out.println(numbers[0]);", "std::cout << (numbers[0]) << std::endl;");
        check(translator, "values[1] = 2.5;", "values[1] = 2.5;");
        check(translator, "names[0] = \"Ada\";", "names[0] = \"Ada\";");
        check(translator, "System.out.print(names[0]);", "std::cout << (names[0]);");
        check(translator, "System.out.println(names[0] + numbers[0]);",
                "std::cout << (names[0]) << (numbers[0]) << std::endl;");
        check(translator, "System.out.println(numbers[0] + numbers[1]);",
                "std::cout << (numbers[0] + numbers[1]) << std::endl;");
        check(translator, "System.out.println(\"Next: \" + numbers[i + 1]);",
                "std::cout << \"Next: \" << (numbers[i + 1]) << std::endl;");
        check(translator, "int [] empty = new int [ 0 ];", "std::vector<int> empty(0);");
        check(translator, "int[] sized = new int[count];", "std::vector<int> sized(count);");
        check(translator, "int[] empty = {};", "std::vector<int> empty = {};");

        String code = translator.translate(String.join("\n",
                "import java.util.Scanner;",
                "public class ArrayExample {",
                "public static void main(String[] args) {",
                "Scanner scanner = new Scanner(System.in);",
                "int count = Integer.valueOf(scanner.nextLine());",
                "int[] numbers = new int[count];",
                "double[] values = new double[10];",
                "String[] names = new String[3];",
                "String name = scanner.nextLine();",
                "names[0] = name;",
                "numbers[0] = 10;",
                "System.out.println(names[0] + numbers[0]);",
                "}", "}"));
        require(code.contains("#include <vector>"), "Arrays need the vector header");
        require(code.indexOf("#include <vector>") == code.lastIndexOf("#include <vector>"),
                "Multiple arrays only need one vector header");
        require(code.contains("#include <string>") && code.contains("#include <iostream>"),
                "Scanner and string array headers must be preserved");
        require(code.contains("std::getline(std::cin, countInputLine);")
                && code.contains("std::getline(std::cin, name);"), "Scanner input still works alongside arrays");
        require(code.contains("std::cout << (names[0]) << (numbers[0]) << std::endl;"),
                "String array elements still concatenate in print statements");
        String numericOnly = translator.translate("int[] numbers = new int[5];");
        require(numericOnly.contains("#include <vector>") && !numericOnly.contains("#include <string>"),
                "Numeric arrays do not need string");
        String noArrays = translator.translate("System.out.println(5);");
        require(!noArrays.contains("#include <vector>"), "Array header state resets between programs");
        check(translator, "System.out.println(names[0] + 1);", "std::cout << (names[0] + 1) << std::endl;");
        require(!translator.translate("System.out.println(\"std::vector<int>\");").contains("#include <vector>"),
                "Mentioning vector in a string does not use arrays");
    }
}
