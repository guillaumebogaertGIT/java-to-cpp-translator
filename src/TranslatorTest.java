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
        System.out.println("Passed " + checks + " translation checks.");
    }
}
