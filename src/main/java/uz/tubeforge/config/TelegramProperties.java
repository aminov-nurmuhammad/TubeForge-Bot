package uz.tubeforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties("tubeforge.telegram")
public record TelegramProperties(
        String token,
        String apiBaseUrl,
        boolean pollingEnabled,
        int pollingTimeoutSeconds,
        long maxUploadBytes,
        int maxConcurrentUpdates,
        int maxQueuedUpdates,
        int apiMaxRetries,
        Duration apiRetryBaseDelay
) {
    public TelegramProperties {
        token = token == null ? "" : token.strip();
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.telegram.org" : apiBaseUrl.strip();
        pollingTimeoutSeconds = Math.max(1, Math.min(50, pollingTimeoutSeconds));
        maxUploadBytes = Math.max(1_000_000, maxUploadBytes);
        maxConcurrentUpdates = Math.max(1, Math.min(32, maxConcurrentUpdates));
        maxQueuedUpdates = Math.max(50, Math.min(20_000, maxQueuedUpdates));
        apiMaxRetries = Math.max(0, Math.min(5, apiMaxRetries));
        apiRetryBaseDelay = apiRetryBaseDelay == null || apiRetryBaseDelay.isZero()
                || apiRetryBaseDelay.isNegative() ? Duration.ofMillis(250) : apiRetryBaseDelay;
    }

    public boolean configured() {
        return StringUtils.hasText(token) && !"test-token".equals(token);
    }

    public String botApiUrl() {
        return apiBaseUrl.replaceAll("/+$", "") + "/bot" + token;
    }
}
