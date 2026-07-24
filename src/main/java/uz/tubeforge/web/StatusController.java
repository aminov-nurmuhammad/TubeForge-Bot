package uz.tubeforge.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class StatusController {
    @GetMapping("/")
    public Map<String, Object> status() {
        return Map.of(
                "name", "TubeForge Bot",
                "status", "running",
                "version", "5.0.0",
                "timestamp", Instant.now().toString()
        );
    }
}
