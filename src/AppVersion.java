import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppVersion {
    private static final String RESOURCE = "/app.properties";
    private static final String FALLBACK_VERSION = "0.0.0";

    private AppVersion() {
    }

    public static String current() {
        Properties properties = new Properties();
        try (InputStream input = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                return FALLBACK_VERSION;
            }
            properties.load(input);
            String version = properties.getProperty("app.version");
            return version == null || version.isBlank() ? FALLBACK_VERSION : version.trim();
        } catch (IOException exception) {
            return FALLBACK_VERSION;
        }
    }
}
