public class UpdateCheckerTest {
    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        checkCompare("1.0.2", "1.0.2", 0);
        checkCompare("1.0.3", "1.0.2", 1);
        checkCompare("1.1.0", "1.0.9", 1);
        checkCompare("2.0.0", "1.9.9", 1);
        checkCompare("1.0.10", "1.0.9", 1);
        checkLatestReleaseParsing();
        checkInvalidVersionRejected();
        System.out.println("Passed " + checks + " update/version checks.");
    }

    private static void checkCompare(String left, String right, int expectedSign) throws Exception {
        int actual = UpdateChecker.Version.parse(left).compareTo(UpdateChecker.Version.parse(right));
        if (Integer.signum(actual) != expectedSign) {
            throw new AssertionError(left + " compared to " + right + " expected sign " + expectedSign
                    + " but was " + Integer.signum(actual));
        }
        checks++;
    }

    private static void checkLatestReleaseParsing() throws Exception {
        String json = "{"
                + "\"tag_name\":\"v1.0.3\","
                + "\"html_url\":\"https://github.com/guillaumebogaertGIT/java-to-cpp-translator/releases/tag/v1.0.3\","
                + "\"assets\":["
                + "{\"name\":\"notes.txt\",\"browser_download_url\":\"https://example.com/notes.txt\"},"
                + "{\"name\":\"Java to C++ Translator-1.0.3.exe\","
                + "\"browser_download_url\":\"https://github.com/guillaumebogaertGIT/java-to-cpp-translator/releases/download/v1.0.3/Java-to-Cpp-Translator-1.0.3.exe\"}"
                + "]}";
        UpdateChecker.UpdateResult result = UpdateChecker.fromLatestReleaseJson("1.0.2", json);
        require(result.updateAvailable(), "Newer release should be reported");
        require(result.latestVersion().equals("1.0.3"), "Tag should normalize without v prefix");
        require(result.downloadUrl().endsWith(".exe"), "Installer asset should be preferred");
    }

    private static void checkInvalidVersionRejected() throws Exception {
        try {
            UpdateChecker.Version.parse("v1.0.x");
            throw new AssertionError("Invalid version tag should fail");
        } catch (UpdateChecker.UpdateCheckException expected) {
            checks++;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
