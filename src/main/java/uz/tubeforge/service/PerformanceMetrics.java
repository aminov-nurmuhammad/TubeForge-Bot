package uz.tubeforge.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class PerformanceMetrics {
    private final Counter metadataHits;
    private final Counter metadataMisses;
    private final Counter artifactHits;
    private final Counter artifactMisses;
    private final Counter artifactStores;
    private final Counter coalescedJobs;
    private final Counter dispatchedUpdates;
    private final Counter rejectedUpdates;
    private final Counter telegramRetries;
    private final Counter inspectionCooldownHits;
    private final Counter rateLimitedLinks;
    private final Counter aiLocal;
    private final Counter aiOllama;
    private final Counter instantReelRequests;
    private final Counter instantReelDeliveries;
    private final Counter instantReelCacheDeliveries;
    private final Counter instantReelFailures;
    private final Timer instantReelLatency;

    public PerformanceMetrics(MeterRegistry registry) {
        metadataHits = registry.counter("tubeforge.cache.metadata.hits");
        metadataMisses = registry.counter("tubeforge.cache.metadata.misses");
        artifactHits = registry.counter("tubeforge.cache.artifact.hits");
        artifactMisses = registry.counter("tubeforge.cache.artifact.misses");
        artifactStores = registry.counter("tubeforge.cache.artifact.stores");
        coalescedJobs = registry.counter("tubeforge.jobs.coalesced");
        dispatchedUpdates = registry.counter("tubeforge.telegram.updates.dispatched");
        rejectedUpdates = registry.counter("tubeforge.telegram.updates.rejected");
        telegramRetries = registry.counter("tubeforge.telegram.api.retries");
        inspectionCooldownHits = registry.counter("tubeforge.inspection.cooldown.hits");
        rateLimitedLinks = registry.counter("tubeforge.access.links.rate_limited");
        aiLocal = registry.counter("tubeforge.ai.requests", "provider", "local");
        aiOllama = registry.counter("tubeforge.ai.requests", "provider", "ollama");
        instantReelRequests = registry.counter("tubeforge.instagram.instant.requests");
        instantReelDeliveries = registry.counter("tubeforge.instagram.instant.deliveries");
        instantReelCacheDeliveries = registry.counter("tubeforge.instagram.instant.cache_deliveries");
        instantReelFailures = registry.counter("tubeforge.instagram.instant.failures");
        instantReelLatency = registry.timer("tubeforge.instagram.instant.delivery_latency");
    }

    public void metadataHit() { metadataHits.increment(); }
    public void metadataMiss() { metadataMisses.increment(); }
    public void artifactHit() { artifactHits.increment(); }
    public void artifactMiss() { artifactMisses.increment(); }
    public void artifactStore() { artifactStores.increment(); }
    public void coalescedJob() { coalescedJobs.increment(); }
    public void dispatchedUpdate() { dispatchedUpdates.increment(); }
    public void rejectedUpdate() { rejectedUpdates.increment(); }
    public void telegramRetry() { telegramRetries.increment(); }
    public void inspectionCooldownHit() { inspectionCooldownHits.increment(); }
    public void rateLimitedLink() { rateLimitedLinks.increment(); }
    public void aiLocal() { aiLocal.increment(); }
    public void aiOllama() { aiOllama.increment(); }
    public void instantReelRequest() { instantReelRequests.increment(); }
    public void instantReelFailure() { instantReelFailures.increment(); }
    public void instantReelDelivered(Duration latency, boolean cached) {
        instantReelDeliveries.increment();
        if (cached) instantReelCacheDeliveries.increment();
        instantReelLatency.record(latency.isNegative() ? Duration.ZERO : latency);
    }

    public Snapshot snapshot() {
        return new Snapshot(value(metadataHits), value(metadataMisses), value(artifactHits),
                value(artifactMisses), value(artifactStores), value(coalescedJobs),
                value(dispatchedUpdates), value(rejectedUpdates), value(telegramRetries),
                value(inspectionCooldownHits), value(rateLimitedLinks), value(aiLocal), value(aiOllama),
                value(instantReelRequests), value(instantReelDeliveries), value(instantReelCacheDeliveries),
                value(instantReelFailures), averageMillis(instantReelLatency));
    }

    private long value(Counter counter) {
        return Math.round(counter.count());
    }

    private long averageMillis(Timer timer) {
        return timer.count() == 0 ? 0 : Math.round(timer.mean(TimeUnit.MILLISECONDS));
    }

    public record Snapshot(
            long metadataHits,
            long metadataMisses,
            long artifactHits,
            long artifactMisses,
            long artifactStores,
            long coalescedJobs,
            long dispatchedUpdates,
            long rejectedUpdates,
            long telegramRetries,
            long inspectionCooldownHits,
            long rateLimitedLinks,
            long aiLocal,
            long aiOllama,
            long instantReelRequests,
            long instantReelDeliveries,
            long instantReelCacheDeliveries,
            long instantReelFailures,
            long instantReelAverageMillis
    ) {}
}
