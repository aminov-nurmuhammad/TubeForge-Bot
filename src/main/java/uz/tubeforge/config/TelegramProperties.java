package uz.tubeforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("tubeforge.telegram")
public record TelegramProperties(
        String token,
        String apiBaseUrl,
        boolean pollingEnabled,
        int pollingTimeoutSeconds,
        long maxUploadBytes
) {
    public TelegramProperties {
        token = token == null ? "" : token.strip();
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.telegram.org" : apiBaseUrl.strip();
        pollingTimeoutSeconds = Math.max(1, Math.min(50, pollingTimeoutSeconds));
        maxUploadBytes = Math.max(1_000_000, maxUploadBytes);
    }

    public boolean configured() {
        return StringUtils.hasText(token) && !"test-token".equals(token);
    }

    public String botApiUrl() {
        return apiBaseUrl.replaceAll("/+$", "") + "/bot" + token;
    }
}
