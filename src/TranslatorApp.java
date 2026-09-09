import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/** Desktop interface. All translation rules remain in Translator. */
public class TranslatorApp extends Application {
    private final TextArea javaEditor = new TextArea();
    private final TextArea cppOutput = new TextArea();
    private final Label status = new Label("Ready. Paste Java code or open a .java file.");
    private String outputFileName = "Translated.cpp";

    @Override
    public void start(Stage stage) {
        // Let Windows provide minimize, maximize/restore, and close controls.
        stage.initStyle(StageStyle.DECORATED);
        stage.setResizable(true);
        Platform.setImplicitExit(true);
        stage.setOnCloseRequest(event -> Platform.exit());
        Label title = new Label("Java to C++ Translator");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Write Java, translate, and save your C++ code.");
        subtitle.getStyleClass().add("subtitle");

        Button translate = new Button("Translate");
        translate.setId("translateButton");
        translate.setDefaultButton(true);
        translate.getStyleClass().add("primary-button");
        translate.setOnAction(event -> translateCode());
        Button open = new Button("Open Java File");
        open.setOnAction(event -> openJavaFile(stage));
        Button save = new Button("Save C++ File");
        save.setId("saveButton");
        save.disableProperty().bind(cppOutput.textProperty().isEmpty());
        save.setOnAction(event -> saveCppFile(stage));
        Button clear = new Button("Clear");
        clear.setId("clearButton");
        clear.setOnAction(event -> {
            javaEditor.clear();
            cppOutput.clear();
            outputFileName = "Translated.cpp";
            showStatus("Cleared. Ready for Java code.", false);
            javaEditor.requestFocus();
        });
        HBox buttons = new HBox(10, translate, open, save, clear);
        buttons.getStyleClass().add("toolbar");
        VBox header = new VBox(10, title, subtitle, buttons);
        header.getStyleClass().add("app-header");

        javaEditor.setId("javaEditor");
        javaEditor.setPromptText("Paste your Java code here, or choose Open Java File...");
        cppOutput.setId("cppOutput");
        cppOutput.setEditable(false);
        cppOutput.setPromptText("Generated C++ will appear here.");
        for (TextArea area : new TextArea[] {javaEditor, cppOutput}) {
            area.setWrapText(false);
            area.getStyleClass().add("code-editor");
        }
        // Never let Save silently export output from an older version of the input.
        javaEditor.textProperty().addListener((observable, oldText, newText) -> {
            cppOutput.clear();
            showStatus("Java code changed. Click Translate to generate C++.", false);
        });
        SplitPane editors = new SplitPane(codePanel("JAVA INPUT", javaEditor, true), codePanel("C++ OUTPUT", cppOutput, false));
        editors.setDividerPositions(0.5);

        status.setId("statusMessage");
        status.setWrapText(true);
        status.getStyleClass().add("status-message");
        status.setMaxWidth(Double.MAX_VALUE);
        BorderPane root = new BorderPane(editors, header, null, status, null);
        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(TranslatorApp.class.getResource("/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(480);
        stage.setTitle("Java to C++ Translator");
        stage.show();
    }

    private VBox codePanel(String heading, TextArea area, boolean java) {
        Label label = new Label(heading);
        label.getStyleClass().add("panel-title");
        StackPane icon = new StackPane();
        icon.getStyleClass().addAll("language-icon", java ? "java-icon" : "cpp-icon");
        icon.setAccessibleText(java ? "Java" : "C++");
        if (java) {
            // Small coffee-cup symbol drawn with JavaFX; no image dependency.
            SVGPath cup = new SVGPath();
            cup.setContent("M 4 11 L 4 16 Q 4 20 10 20 Q 16 20 16 16 L 16 11 Z "
                    + "M 16 12 L 18 12 Q 22 12 21 15 Q 20 18 16 17 "
                    + "M 2 23 L 19 23 M 8 8 C 4 4 12 4 9 0 M 13 8 C 10 5 17 4 14 1");
            cup.getStyleClass().add("java-cup");
            icon.getChildren().add(cup);
        } else {
            Label cpp = new Label("C++");
            cpp.getStyleClass().add("cpp-symbol");
            icon.getChildren().add(cpp);
        }
        HBox panelHeader = new HBox(10, icon, label);
        panelHeader.getStyleClass().add("panel-header");
        VBox panel = new VBox(panelHeader, area);
        panel.getStyleClass().add("code-panel");
        panel.setMinWidth(200);
        VBox.setVgrow(area, Priority.ALWAYS);
        return panel;
    }

    private void translateCode() {
        if (javaEditor.getText().isBlank()) {
            showStatus("Enter Java code before translating.", true);
            return;
        }
        try {
            cppOutput.setText(new Translator().translate(javaEditor.getText()));
            showStatus("Translation complete. C++ is ready to save; compilation is a separate step.", false);
        } catch (RuntimeException exception) {
            cppOutput.clear();
            showError("Translation failed", exception);
        }
    }

    private void openJavaFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Java File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java source (*.java)", "*.java"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        try {
            String code = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            javaEditor.setText(code);
            cppOutput.clear();
            outputFileName = file.getName().replaceFirst("(?i)\\.java$", "") + ".cpp";
            showStatus("Opened " + file.getName() + ". Click Translate to generate C++.", false);
        } catch (IOException | SecurityException exception) {
            showError("Could not open file", exception);
        }
    }

    private void saveCppFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save C++ File");
        chooser.setInitialFileName(outputFileName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C++ source (*.cpp)", "*.cpp"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".cpp")) {
            showStatus("Please save with a .cpp extension (for example, Translated.cpp).", true);
            return;
        }
        try {
            Files.writeString(file.toPath(), cppOutput.getText(), StandardCharsets.UTF_8);
            showStatus("Saved C++ to " + file.getAbsolutePath(), false);
        } catch (IOException | SecurityException exception) {
            showError("Could not save file", exception);
        }
    }

    private void showError(String action, Exception exception) {
        String detail = exception.getMessage();
        showStatus(action + ": " + (detail == null ? "Please check the input and try again." : detail), true);
    }

    private void showStatus(String message, boolean error) {
        status.setText(message);
        status.pseudoClassStateChanged(PseudoClass.getPseudoClass("error"), error);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
