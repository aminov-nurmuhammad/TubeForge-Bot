package uz.tubeforge.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import uz.tubeforge.config.MediaProperties;
import uz.tubeforge.media.ManagedProcessRunner;
import uz.tubeforge.media.ProcessResult;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Component("mediaTools")
public class MediaToolsHealthIndicator implements HealthIndicator {
    private final MediaProperties properties;
    private final ManagedProcessRunner runner;

    public MediaToolsHealthIndicator(MediaProperties properties, ManagedProcessRunner runner) {
        this.properties = properties;
        this.runner = runner;
    }

    @Override
    public Health health() {
        ProcessResult ytDlp = check(properties.ytDlpPath(), "--version");
        ProcessResult ffmpeg = check(properties.ffmpegPath(), "-version");
        ProcessResult ffprobe = check(properties.ffprobePath(), "-version");
        boolean cookiesReady = !properties.hasCookiesFile()
                || java.nio.file.Files.isRegularFile(properties.cookiesFile());
        Health.Builder health = ytDlp.successful() && ffmpeg.successful() && ffprobe.successful() && cookiesReady
                ? Health.up() : Health.down();
        return health.withDetail("ytDlp", summary(ytDlp))
                .withDetail("ffmpeg", summary(ffmpeg))
                .withDetail("ffprobe", summary(ffprobe))
                .withDetail("cookies", properties.hasCookiesFile() ? (cookiesReady ? "configured" : "file missing") : "not configured")
                .withDetail("storage", properties.storagePath().toAbsolutePath().normalize().toString())
                .build();
    }

    private ProcessResult check(String executable, String argument) {
        try {
            return runner.capture(List.of(executable, argument), Path.of("."), Duration.ofSeconds(10));
        } catch (Exception e) {
            return new ProcessResult(-1, e.getMessage(), false, false);
        }
    }

    private String summary(ProcessResult result) {
        if (!result.successful()) return "unavailable";
        String output = result.output().lines().findFirst().orElse("available");
        return output.length() > 100 ? output.substring(0, 100) : output;
    }
}
