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
    public boolean configured() {
        return StringUtils.hasText(token) && !"test-token".equals(token);
    }

    public String botApiUrl() {
        return apiBaseUrl.replaceAll("/+$", "") + "/bot" + token;
    }
}
