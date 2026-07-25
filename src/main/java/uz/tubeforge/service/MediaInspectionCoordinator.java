package uz.tubeforge.service;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import uz.tubeforge.media.MediaProcessingException;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.media.MediaInspectionService;
import uz.tubeforge.media.ParsedMediaUrl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MediaInspectionCoordinator {
    private final MediaInspectionService inspection;
    private final PerformanceMetrics metrics;
    private final Clock clock;
    private final ConcurrentHashMap<String, CompletableFuture<MediaInfo>> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedFailure> recentFailures = new ConcurrentHashMap<>();

    public MediaInspectionCoordinator(MediaInspectionService inspection, PerformanceMetrics metrics, Clock clock) {
        this.inspection = inspection;
        this.metrics = metrics;
        this.clock = clock;
    }

    public MediaInfo inspect(ParsedMediaUrl url) {
        CachedFailure recent = recentFailures.get(url.normalizedUrl());
        if (recent != null) {
            if (recent.expiresAt().isAfter(clock.instant())) {
                metrics.inspectionCooldownHit();
                throw new MediaProcessingException(recent.code(), recent.message());
            }
            recentFailures.remove(url.normalizedUrl(), recent);
        }
        CompletableFuture<MediaInfo> mine = new CompletableFuture<>();
        CompletableFuture<MediaInfo> existing = inFlight.putIfAbsent(url.normalizedUrl(), mine);
        if (existing != null) {
            metrics.coalescedJob();
            try {
                return existing.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                throw e;
            }
        }
        try {
            MediaInfo info = inspection.inspect(url);
            recentFailures.remove(url.normalizedUrl());
            mine.complete(info);
            return info;
        } catch (MediaProcessingException e) {
            recentFailures.put(url.normalizedUrl(), new CachedFailure(e.getCode(), e.getUserMessage(),
                    clock.instant().plus(cooldownFor(e.getCode()))));
            mine.completeExceptionally(e);
            throw e;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(url.normalizedUrl(), mine);
        }
    }

    public int activeInspections() { return inFlight.size(); }

    public int coolingDownLinks() {
        Instant now = clock.instant();
        return (int) recentFailures.values().stream().filter(value -> value.expiresAt().isAfter(now)).count();
    }

    @Scheduled(fixedDelayString = "PT10M")
    void clearExpiredFailures() {
        Instant now = clock.instant();
        recentFailures.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private Duration cooldownFor(String code) {
        if (code != null && Set.of("PRIVATE_VIDEO", "INSTAGRAM_PRIVATE", "INSTAGRAM_UNAVAILABLE", "MEMBERS_ONLY",
                "AGE_RESTRICTED", "COPYRIGHT_BLOCK", "UNAVAILABLE").contains(code)) {
            return Duration.ofMinutes(10);
        }
        if (code != null && (code.endsWith("_RATE_LIMITED") || code.endsWith("_AUTH_REQUIRED"))) {
            return Duration.ofMinutes(2);
        }
        if (code != null && code.endsWith("_TIMEOUT")) return Duration.ofSeconds(20);
        return Duration.ofSeconds(45);
    }

    private record CachedFailure(String code, String message, Instant expiresAt) {}
}
