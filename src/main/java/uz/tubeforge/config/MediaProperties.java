package uz.tubeforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties("tubeforge.media")
public record MediaProperties(
        String ytDlpPath,
        String ffmpegPath,
        String ffprobePath,
        Path cookiesFile,
        String proxyUrl,
        Path storagePath,
        Duration processTimeout,
        Duration metadataTimeout,
        Duration socketTimeout,
        long maxVideoDurationSeconds,
        int maxPlaylistItems,
        int maxConcurrentJobs,
        int maxConcurrentInspections,
        int concurrentFragments,
        int extractorRetries,
        Duration cacheRetention,
        Duration progressUpdateInterval
) {
    public MediaProperties {
        ytDlpPath = textOrDefault(ytDlpPath, "yt-dlp");
        ffmpegPath = textOrDefault(ffmpegPath, "ffmpeg");
        ffprobePath = textOrDefault(ffprobePath, "ffprobe");
        proxyUrl = proxyUrl == null ? "" : proxyUrl.strip();
        storagePath = storagePath == null ? Path.of("./storage") : storagePath;
        processTimeout = positive(processTimeout, Duration.ofHours(2));
        metadataTimeout = positive(metadataTimeout, Duration.ofSeconds(90));
        socketTimeout = positive(socketTimeout, Duration.ofSeconds(30));
        maxVideoDurationSeconds = Math.max(60, maxVideoDurationSeconds);
        maxPlaylistItems = Math.max(1, Math.min(100, maxPlaylistItems));
        maxConcurrentJobs = Math.max(1, Math.min(16, maxConcurrentJobs));
        maxConcurrentInspections = Math.max(1, Math.min(32, maxConcurrentInspections));
        concurrentFragments = Math.max(1, Math.min(16, concurrentFragments));
        extractorRetries = Math.max(1, Math.min(20, extractorRetries));
        cacheRetention = positive(cacheRetention, Duration.ofHours(24));
        progressUpdateInterval = positive(progressUpdateInterval, Duration.ofSeconds(3));
    }

    public boolean hasCookiesFile() {
        return cookiesFile != null && !cookiesFile.toString().isBlank();
    }

    public boolean hasProxy() {
        return !proxyUrl.isBlank();
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
