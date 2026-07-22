package uz.tubeforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("tubeforge.cache")
public record CacheProperties(
        boolean enabled,
        Duration artifactRetention,
        Duration insightRetention
) {
    public CacheProperties {
        artifactRetention = positive(artifactRetention, Duration.ofDays(30));
        insightRetention = positive(insightRetention, Duration.ofDays(7));
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
