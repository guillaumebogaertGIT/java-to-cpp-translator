import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Desktop interface. All translation rules remain in Translator. */
public class TranslatorApp extends Application {
    static {
        AppDiagnostics.install();
    }
    private final TextArea javaEditor = new TextArea();
    private final TextArea cppOutput = new TextArea();
    private final Label statusIndicator = new Label("●");
    private final Label statusText = new Label("Ready");
    private final Label versionText = new Label("v" + AppVersion.current() + " • Guillaume Bogaert");
    private final Button updateButton = new Button("↻");
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
        updateButton.getStyleClass().add("icon-button");
        updateButton.setId("updateButton");
        updateButton.setTooltip(new Tooltip("Check for updates"));
        updateButton.setAccessibleText("Check for updates");
        updateButton.setFocusTraversable(false);
        updateButton.setOnAction(event -> checkForUpdates());

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
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleRow = new HBox(12, title, titleSpacer, updateButton);
        titleRow.getStyleClass().add("title-row");
        VBox header = new VBox(10, titleRow, subtitle, buttons);
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

        statusIndicator.setId("statusIndicator");
        statusIndicator.getStyleClass().add("status-indicator");
        statusText.setId("statusMessage");
        statusText.setWrapText(true);
        statusText.getStyleClass().add("status-message");
        statusText.setMaxWidth(Double.MAX_VALUE);
        versionText.getStyleClass().add("status-version");

        HBox statusLeft = new HBox(8, statusIndicator, statusText);
        statusLeft.getStyleClass().add("status-left");
        HBox statusBar = new HBox(statusLeft, versionText);
        statusBar.getStyleClass().add("status-bar");
        HBox.setHgrow(statusLeft, Priority.ALWAYS);

        BorderPane root = new BorderPane(editors, header, null, statusBar, null);
        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(TranslatorApp.class.getResource("/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(480);
        stage.setTitle("Java to C++ Translator");
        stage.show();
        System.err.println("Opened TranslatorApp v" + AppVersion.current());
        getParameters().getRaw().stream()
                .filter(argument -> argument.startsWith("--smoke-test="))
                .findFirst()
                .ifPresent(argument -> Platform.runLater(() -> PackagedSmokeTest.run(stage,
                        argument.substring("--smoke-test=".length()))));
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

    private void checkForUpdates() {
        updateButton.setDisable(true);
        showStatus("Checking for updates...", false);
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return new UpdateChecker().checkForUpdates(AppVersion.current());
                    } catch (IOException | InterruptedException | UpdateChecker.UpdateCheckException exception) {
                        if (exception instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        throw new CompletionException(exception);
                    }
                })
                .whenComplete((result, failure) -> Platform.runLater(() -> {
                    updateButton.setDisable(false);
                    if (failure != null) {
                        showStatus("Unable to check for updates. Please try again later.", true);
                        showUpdateError();
                    } else {
                        showUpdateResult(result);
                    }
                }));
    }

    private void showUpdateResult(UpdateChecker.UpdateResult result) {
        if (!result.updateAvailable()) {
            showStatus("You're up to date. Java to C++ Translator v" + result.currentVersion()
                    + " is the latest version.", false);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Check for Updates");
            alert.setHeaderText("You're up to date.");
            alert.setContentText("Java to C++ Translator v" + result.currentVersion()
                    + " is the latest version.");
            alert.showAndWait();
            return;
        }

        showStatus("Update available: v" + result.latestVersion()
                + " is available. You are using v" + result.currentVersion() + ".", false);
        ButtonType download = new ButtonType("Download Update");
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Java to C++ Translator v" + result.latestVersion()
                + " is available.\nYou are currently using v" + result.currentVersion() + ".", download,
                ButtonType.CANCEL);
        alert.setTitle("Update Available");
        alert.setHeaderText("Update available");
        alert.showAndWait()
                .filter(button -> button == download)
                .ifPresent(button -> openUpdatePage(result.downloadUrl()));
    }

    private void showUpdateError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Check for Updates");
        alert.setHeaderText("Unable to check for updates.");
        alert.setContentText("Please try again later.");
        alert.showAndWait();
    }

    private void openUpdatePage(String url) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                showStatus("Download page: " + url, false);
                return;
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            showStatus("Could not open the download page: " + url, true);
        }
    }

    private void showError(String action, Exception exception) {
        String detail = exception.getMessage();
        showStatus(action + ": " + (detail == null ? "Please check the input and try again." : detail), true);
    }

    private void showStatus(String message, boolean error) {
        statusText.setText(message);
        statusIndicator.getStyleClass().removeAll("ready", "error");
        statusIndicator.getStyleClass().add(error ? "error" : "ready");
        statusText.pseudoClassStateChanged(PseudoClass.getPseudoClass("error"), error);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
