package uz.tubeforge.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.Map;

@RestController
public class StatusController {
    private final String version;

    public StatusController(@Value("${info.app.version:unknown}") String version) {
        this.version = version;
    }

    @GetMapping("/")
    public Map<String, Object> status() {
        return Map.of(
                "name", "TubeForge Bot",
                "status", "running",
                "version", version,
                "timestamp", Instant.now().toString()
        );
    }
}
