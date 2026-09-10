import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Keep errors from the console-less Windows launcher in a user-writable location. */
public final class AppDiagnostics {
    private AppDiagnostics() { }

    public static void install() {
        try {
            String local = System.getenv("LOCALAPPDATA");
            Path directory = Path.of(local == null ? System.getProperty("user.home") : local,
                    "Java to C++ Translator", "logs");
            Files.createDirectories(directory);
            Path log = directory.resolve("application.log");
            if (Files.exists(log) && Files.size(log) > 1_048_576) {
                Files.move(log, directory.resolve("application.previous.log"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            System.setErr(new PrintStream(Files.newOutputStream(log,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND), true, StandardCharsets.UTF_8));
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                System.err.println("Uncaught error on " + thread.getName());
                error.printStackTrace(System.err);
            });
            System.err.println(Instant.now() + " Starting v" + AppVersion.current()
                    + " Java " + System.getProperty("java.version"));
        } catch (IOException | SecurityException exception) {
            exception.printStackTrace(System.err);
        }
    }
}
