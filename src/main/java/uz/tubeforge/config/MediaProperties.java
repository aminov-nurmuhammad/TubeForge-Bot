package uz.tubeforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties("tubeforge.media")
public record MediaProperties(
        String ytDlpPath,
        String ffmpegPath,
        String ffprobePath,
        Path storagePath,
        Duration processTimeout,
        Duration metadataTimeout,
        long maxVideoDurationSeconds,
        int maxPlaylistItems,
        int maxConcurrentJobs,
        Duration cacheRetention,
        Duration progressUpdateInterval
) {
}
