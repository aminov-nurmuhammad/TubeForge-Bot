package uz.tubeforge.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MediaToolsStartupReporter {
    private static final Logger log = LoggerFactory.getLogger(MediaToolsStartupReporter.class);

    private final MediaToolsHealthIndicator healthIndicator;

    public MediaToolsStartupReporter(MediaToolsHealthIndicator healthIndicator) {
        this.healthIndicator = healthIndicator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        var health = healthIndicator.health();
        if ("UP".equals(health.getStatus().getCode())) {
            log.info("Media tool preflight passed: {}", health.getDetails());
        } else {
            log.error("Media tool preflight failed: {}. Check YT_DLP_PATH, FFMPEG_PATH and FFPROBE_PATH before sending links.",
                    health.getDetails());
        }
    }
}
