import java.nio.file.Files;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

/** Opt-in check exercised through the native launcher, using its actual scene and runtime. */
final class PackagedSmokeTest {
    private PackagedSmokeTest() { }

    static void run(Stage stage, String report) {
        try {
            var root = stage.getScene().getRoot();
            root.applyCss();
            root.layout();
            Button update = (Button) root.lookup("#updateButton");
            if (!stage.isShowing() || update == null || !update.isVisible()
                    || update.isDisabled() || update.getOnAction() == null
                    || update.localToScreen(update.getBoundsInLocal()) == null) {
                throw new AssertionError("Window or updater button is unavailable");
            }
            TextArea input = (TextArea) root.lookup("#javaEditor");
            TextArea output = (TextArea) root.lookup("#cppOutput");
            input.setText("public class Hello {\n    public static void main(String[] args) {\n"
                    + "        System.out.println(\"Packaged launch OK\");\n    }\n}\n");
            ((Button) root.lookup("#translateButton")).fire();
            if (!output.getText().contains("cout") || !output.getText().contains("Packaged launch OK")
                    || !output.getText().contains("int main(")) {
                throw new AssertionError("Translation failed: " + output.getText());
            }
            // Construction verifies the bundled HTTP and TLS modules needed by the updater.
            new UpdateChecker();
            Files.writeString(Path.of(report), "PASS v" + AppVersion.current()
                    + " TranslatorApp window, updater button, translation, HTTP client\n");
            Platform.exit();
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
