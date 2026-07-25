package uz.tubeforge.service;

import org.springframework.stereotype.Service;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.media.MediaInspectionService;
import uz.tubeforge.media.ParsedMediaUrl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MediaInspectionCoordinator {
    private final MediaInspectionService inspection;
    private final PerformanceMetrics metrics;
    private final ConcurrentHashMap<String, CompletableFuture<MediaInfo>> inFlight = new ConcurrentHashMap<>();

    public MediaInspectionCoordinator(MediaInspectionService inspection, PerformanceMetrics metrics) {
        this.inspection = inspection;
        this.metrics = metrics;
    }

    public MediaInfo inspect(ParsedMediaUrl url) {
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
            mine.complete(info);
            return info;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(url.normalizedUrl(), mine);
        }
    }

    public int activeInspections() { return inFlight.size(); }
}
