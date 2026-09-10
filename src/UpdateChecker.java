import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class UpdateChecker {
    private static final URI LATEST_RELEASE_API = URI.create(
            "https://api.github.com/repos/guillaumebogaertGIT/java-to-cpp-translator/releases/latest");
    private static final String OFFICIAL_RELEASE_PREFIX =
            "https://github.com/guillaumebogaertGIT/java-to-cpp-translator/releases/";
    private static final String LATEST_RELEASE_PAGE =
            "https://github.com/guillaumebogaertGIT/java-to-cpp-translator/releases/latest";

    private final HttpClient httpClient;
    private final Duration timeout;

    public UpdateChecker() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), Duration.ofSeconds(10));
    }

    UpdateChecker(HttpClient httpClient, Duration timeout) {
        this.httpClient = httpClient;
        this.timeout = timeout;
    }

    public UpdateResult checkForUpdates(String currentVersion)
            throws IOException, InterruptedException, UpdateCheckException {
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE_API)
                .timeout(timeout)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Java-to-Cpp-Translator-Update-Checker")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new UpdateCheckException("GitHub returned HTTP " + response.statusCode());
        }
        return fromLatestReleaseJson(currentVersion, response.body());
    }

    static UpdateResult fromLatestReleaseJson(String currentVersion, String json) throws UpdateCheckException {
        String tagName = readStringField(json, "tag_name")
                .orElseThrow(() -> new UpdateCheckException("Latest release did not include a tag name."));
        String releasePage = readStringField(json, "html_url").orElse(LATEST_RELEASE_PAGE);
        Version latestVersion = Version.parse(tagName);
        Version installedVersion = Version.parse(currentVersion);
        if (!isOfficialReleaseUrl(releasePage)) {
            releasePage = LATEST_RELEASE_PAGE;
        }

        String downloadUrl = findInstallerAssetUrl(json).orElse(releasePage);
        if (!isOfficialReleaseUrl(downloadUrl)) {
            downloadUrl = releasePage;
        }

        return new UpdateResult(installedVersion.toString(), latestVersion.toString(),
                latestVersion.compareTo(installedVersion) > 0, downloadUrl);
    }

    private static Optional<String> findInstallerAssetUrl(String json) {
        int assetsIndex = json.indexOf("\"assets\"");
        if (assetsIndex < 0) {
            return Optional.empty();
        }
        List<String> urls = readStringFields(json.substring(assetsIndex), "browser_download_url");
        return urls.stream()
                .filter(UpdateChecker::isOfficialReleaseUrl)
                .filter(UpdateChecker::looksLikeWindowsInstaller)
                .findFirst();
    }

    private static boolean looksLikeWindowsInstaller(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.endsWith(".exe") || lower.endsWith(".msi");
    }

    private static boolean isOfficialReleaseUrl(String url) {
        return url != null && url.startsWith("https://") && url.startsWith(OFFICIAL_RELEASE_PREFIX);
    }

    private static Optional<String> readStringField(String json, String fieldName) {
        return readStringFields(json, fieldName).stream().findFirst();
    }

    private static List<String> readStringFields(String json, String fieldName) {
        List<String> values = new ArrayList<>();
        String marker = "\"" + fieldName + "\"";
        int index = 0;
        while ((index = json.indexOf(marker, index)) >= 0) {
            int colon = json.indexOf(':', index + marker.length());
            if (colon < 0) {
                break;
            }
            int quote = json.indexOf('"', colon + 1);
            if (quote < 0) {
                break;
            }
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            for (int i = quote + 1; i < json.length(); i++) {
                char ch = json.charAt(i);
                if (escaped) {
                    value.append(ch);
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    values.add(value.toString());
                    index = i + 1;
                    break;
                } else {
                    value.append(ch);
                }
            }
            index++;
        }
        return values;
    }

    public static final class UpdateResult {
        private final String currentVersion;
        private final String latestVersion;
        private final boolean updateAvailable;
        private final String downloadUrl;

        UpdateResult(String currentVersion, String latestVersion, boolean updateAvailable, String downloadUrl) {
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.updateAvailable = updateAvailable;
            this.downloadUrl = downloadUrl;
        }

        public String currentVersion() {
            return currentVersion;
        }

        public String latestVersion() {
            return latestVersion;
        }

        public boolean updateAvailable() {
            return updateAvailable;
        }

        public String downloadUrl() {
            return downloadUrl;
        }
    }

    public static final class Version implements Comparable<Version> {
        private final int major;
        private final int minor;
        private final int patch;

        private Version(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        public static Version parse(String value) throws UpdateCheckException {
            if (value == null) {
                throw new UpdateCheckException("Version is missing.");
            }
            String normalized = value.trim();
            if (normalized.startsWith("v") || normalized.startsWith("V")) {
                normalized = normalized.substring(1);
            }
            String[] pieces = normalized.split("\\.");
            if (pieces.length != 3) {
                throw new UpdateCheckException("Version must use major.minor.patch: " + value);
            }
            try {
                return new Version(parsePart(pieces[0]), parsePart(pieces[1]), parsePart(pieces[2]));
            } catch (NumberFormatException exception) {
                throw new UpdateCheckException("Version contains a non-numeric part: " + value);
            }
        }

        private static int parsePart(String piece) {
            if (piece.isBlank()) {
                throw new NumberFormatException("blank");
            }
            int value = Integer.parseInt(piece);
            if (value < 0) {
                throw new NumberFormatException("negative");
            }
            return value;
        }

        @Override
        public int compareTo(Version other) {
            if (major != other.major) {
                return Integer.compare(major, other.major);
            }
            if (minor != other.minor) {
                return Integer.compare(minor, other.minor);
            }
            return Integer.compare(patch, other.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    public static class UpdateCheckException extends Exception {
        public UpdateCheckException(String message) {
            super(message);
        }
    }
}
