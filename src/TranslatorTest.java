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
        System.out.println("Passed " + checks + " translation checks.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
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
}
