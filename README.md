# Java-to-C++ Translator

A small student project written in Java that translates a limited Java subset into C++ source code. This is a **source-to-source translator** (or transpiler), not a complete Java compiler. The intended feature set is complete; the project focuses on readable translation rules rather than full language compatibility.

## Requirements

- JDK 17 or newer, with `java` and `javac` on your PATH. Development tests used JDK 21.
- Optional: a C++11-or-newer compiler, such as `g++`, to compile generated output.
- No external Java libraries, Maven, or Gradle are required.

Run all commands below from the project root. Java commands work in PowerShell and typical Unix shells.

## Compile and run

Compile the translator into a separate build directory:

```text
javac -d build src/Main.java src/Translator.java
```

Translate the included demo:

```text
java -cp build Main
```

By default, this reads `Example.java` and **overwrites `Example.cpp`**. To choose filenames:

```text
java -cp build Main tests/MethodExample.java build/MethodExample.cpp
```

Paths are relative to your current directory; quote paths containing spaces. Missing output directories are created automatically. Use different input and output paths to avoid overwriting your Java source.

To compile the C++ demo if `g++` is installed:

```text
g++ -std=c++11 Example.cpp -o build/example
```

Run it with `./build/example` on Linux/macOS or `.\build\example.exe` in Windows PowerShell.

The translator only writes source code; it does not invoke a C++ compiler. “Translation complete” means a file was written, not that the generated C++ has been validated. On failure, `Main` prints an error and stack trace; it does not currently set a nonzero process exit code.

## Demo: Java input and generated C++

The root [Example.java](Example.java) and [Example.cpp](Example.cpp) form a reproducible input/output pair. Run `java -cp build Main` to regenerate the C++ file.

Java input:

```java
public class Example {
    public static void main(String[] args) {
        int total = 0;
        for (int i = 0; i < 5; i++) {
            total = total + i;
            System.out.println(total);
        }

        while (total < 10) {
            total = total + 1;
            System.out.println("still going");
        }
    }
}
```

Generated C++ (including the translator's simple indentation):

```cpp
#include <iostream>

int main() {
    int total = 0;
    for (int i = 0; i < 5; i++) {
    total = total + i;
    std::cout << total << std::endl;
}
    while (total < 10) {
    total = total + 1;
    std::cout << "still going" << std::endl;
}
}
```

Expected output:

```text
0
1
3
6
10
```

The `while` body does not execute: the preceding loop already brings `total` to 10. C++ implicitly returns zero when execution reaches the end of `main`.

Additional Java examples are [PrintExample.java](tests/PrintExample.java) and [MethodExample.java](tests/MethodExample.java).

## Supported Java subset

Use one public class containing `public static void main(String[] args)` and optional simple public static methods. The outer class wrapper is removed; methods become C++ free functions.

| Feature | Supported forms / translation |
| --- | --- |
| Variables | Simple `int`, `double`, `boolean`, and `String` declarations, with or without initialization; `boolean` becomes `bool`, `String` becomes `std::string`. |
| Expressions | Basic arithmetic, comparisons, assignments, and calls whose syntax is shared with C++. Expressions are not fully parsed or type-checked. |
| Control flow | Braced `if`, `else if`, `else`, traditional `for`, and `while` blocks. |
| Console output | `System.out.print(expression)` and `System.out.println(expression)` become stream output; empty `println()` prints a newline. |
| Print concatenation | Recognizes string literals, tracked string variables, and string array elements. Preserves numeric addition before concatenation: `2 + 3 + " items"` prints `5 items`; `"Digits: " + 2 + 3` prints `Digits: 23`. Handles simple parentheses and quoted plus signs. |
| Scanner setup | `import java.util.Scanner;` and `Scanner scanner = new Scanner(System.in);` are removed. Other Scanner variable names work too. |
| Scanner text | `String name = scanner.nextLine();` becomes a declaration and `std::getline(std::cin, name)`. |
| Scanner numbers | `int age = Integer.valueOf(scanner.nextLine());` and `double value = Double.valueOf(scanner.nextLine());` read a whole line and convert with `std::stoi` / `std::stod`. |
| Arrays | One-dimensional `int[]`, `double[]`, and `String[]` allocations become `std::vector<T>`; integer brace initializers such as `int[] numbers = {1, 2, 3};` also work. |
| Array access | Element reads and assignments retain `numbers[i]` syntax. Known array variables' `.length` becomes `.size()` in simple expressions, including loop conditions and allocation sizes. |
| Methods | Single-line `public static` signatures returning `void`, `int`, `double`, `boolean`, or `String`; scalar parameters use those types except `void`, which is not a Java parameter type. |
| Returns and calls | Basic `return expression;`, `return;` in void helpers, and unqualified calls such as `square(5)`. Forward declarations allow definitions after calls. Simple string concatenation in a String return uses `std::ostringstream`. |

Headers are selected from the translated code and used features: `<iostream>`, `<string>`, `<vector>`, and `<sstream>`.

## Formatting conventions

- Put each statement on its own line. Keep declarations, print calls, returns, and method signatures on one line.
- Put `{` at the end of class, method, and control-flow opening lines. Put closing braces on their own lines. Write `else` / `else if` on a separate line after the previous closing brace.
- Always use braces for control flow. Use a space before the opening parenthesis in `if (`, `for (`, and `while (`.
- Use ordinary spaces between types and variable names. Stick to simple ASCII identifiers such as `total` or `student_name`.
- Prefer standalone `//` comments. Avoid trailing comments on recognized statements and multiline block comments containing code-like text. The length rewrite protects comments and literals, but the rest of the translator is not a full comment parser.

## Unsupported features and known limitations

- No objects, instance methods, constructors, inheritance, interfaces, generics, overloading, packages, or general Java library translation. The public class is only a wrapper.
- No multidimensional arrays, ArrayList, array parameters/returns, array reference assignment semantics, alternate `int numbers[]` declarations, or general array initialization forms. Array-size expressions must already translate to usable C++.
- No Scanner `nextInt`, `nextDouble`, `next`, `hasNext`, `close`, file input, or input into already-declared variables through these special rules.
- No multiline method signatures, annotations, `throws` clauses, varargs, or class-qualified calls. Overloads and unsupported public static signatures/parameters are rejected; other unsupported code may pass through unchanged and fail in C++.
- No complete expression parser or type checker. String method-return types are not inferred inside larger concatenations. General Java string operations, equality semantics, and concatenation in ordinary assignments are not translated. Complex expressions and side effects are outside the supported subset.
- Variable tracking resets at each method, but nested scopes and shadowing are not modeled. Only previously recognized local array names receive the length rewrite; object fields and expressions such as `(numbers).length` are unsupported.
- Numeric vector elements start at zero. String vector elements start as empty strings, unlike Java's `null`. Vector `[]` does not check bounds. Vector `.size()` is unsigned, unlike Java's integer `.length`, so negative comparisons and subtraction can differ.
- Numeric input conversion and end-of-input errors do not exactly match Java. For example, C++ conversion can accept a numeric prefix followed by text. Use valid input for demonstrations.
- Numeric overflow, decimal formatting, and boolean printing can differ between languages. Ordinary C++ stream output prints booleans as `1`/`0`; concatenated String returns enable `true`/`false` formatting.
- Java command-line arguments are discarded. An early bare `return;` in Java `main` is not converted to C++ `return 0;`.
- Blank lines and original indentation are not preserved. Output is intentionally basic. Generated headers are partly detected through text matching, so a literal mentioning C++ names can cause an unnecessary header.

## Tests

Compile and run every automated translation check:

```text
javac -d build src/Main.java src/Translator.java src/TranslatorTest.java
java -cp build TranslatorTest
```

The current suite reports `Passed 103 translation checks.` It tests print concatenation, Scanner patterns, arrays, lengths, methods, headers, and state resets. A failed check throws an assertion error; no test framework is required.

Compile and run the Java examples:

```text
javac -d build Example.java tests/PrintExample.java tests/MethodExample.java
java -cp build Example
java -cp build PrintExample
java -cp build MethodExample
```

These checks validate translated text and Java examples, not automatic Java/C++ runtime equivalence. Compile generated C++ separately when a C++ compiler is available.

## Architecture

| File | Responsibility |
| --- | --- |
| `src/Main.java` | Command-line entry point: chooses paths, invokes translation, and reports success/errors. |
| `src/Translator.java` | Reads/writes files, processes source line by line, applies focused translation helpers, tracks a few variable types, and adds headers and method declarations. |
| `src/TranslatorTest.java` | Plain Java assertions for the supported translation rules. |
| `Example.java`, `Example.cpp` | Default Java demo and its generated C++ output. |
| `tests/PrintExample.java`, `tests/MethodExample.java` | Runnable Java examples for printing and helper methods. |
| `.gitignore` | Excludes compiled artifacts and build directories; the C++ demo remains tracked. |

Translation flows through `Main` → `translateFile()` → `translate()` → `translateLine()` and its helpers → C++ text. File handling is separate from text translation, which makes unit tests straightforward.

**Student explanation:** “My project is a rule-based transpiler. It reads Java as text, recognizes a small set of statement patterns, and writes equivalent C++ for that subset. It remembers some variable types to distinguish arithmetic from string concatenation and to recognize arrays. Unlike a full compiler, it does not build an abstract syntax tree or check the whole program's meaning, so its supported syntax and limitations are explicit.”
