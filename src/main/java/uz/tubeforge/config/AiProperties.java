package uz.tubeforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("tubeforge.ai")
public record AiProperties(
        String provider,
        String baseUrl,
        String model,
        Duration timeout,
        int maxTranscriptCharacters
) {
    public AiProperties {
        provider = text(provider, "local").toLowerCase(java.util.Locale.ROOT);
        baseUrl = text(baseUrl, "http://localhost:11434");
        model = text(model, "qwen3:4b");
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofMinutes(3) : timeout;
        maxTranscriptCharacters = Math.max(4_000, Math.min(120_000, maxTranscriptCharacters));
    }

    public boolean ollama() { return "ollama".equals(provider); }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
