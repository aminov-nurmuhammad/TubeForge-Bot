package uz.tubeforge.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PerformanceMetrics {
    private final Counter metadataHits;
    private final Counter metadataMisses;
    private final Counter artifactHits;
    private final Counter artifactMisses;
    private final Counter artifactStores;
    private final Counter coalescedJobs;
    private final Counter aiLocal;
    private final Counter aiOllama;

    public PerformanceMetrics(MeterRegistry registry) {
        metadataHits = registry.counter("tubeforge.cache.metadata.hits");
        metadataMisses = registry.counter("tubeforge.cache.metadata.misses");
        artifactHits = registry.counter("tubeforge.cache.artifact.hits");
        artifactMisses = registry.counter("tubeforge.cache.artifact.misses");
        artifactStores = registry.counter("tubeforge.cache.artifact.stores");
        coalescedJobs = registry.counter("tubeforge.jobs.coalesced");
        aiLocal = registry.counter("tubeforge.ai.requests", "provider", "local");
        aiOllama = registry.counter("tubeforge.ai.requests", "provider", "ollama");
    }

    public void metadataHit() { metadataHits.increment(); }
    public void metadataMiss() { metadataMisses.increment(); }
    public void artifactHit() { artifactHits.increment(); }
    public void artifactMiss() { artifactMisses.increment(); }
    public void artifactStore() { artifactStores.increment(); }
    public void coalescedJob() { coalescedJobs.increment(); }
    public void aiLocal() { aiLocal.increment(); }
    public void aiOllama() { aiOllama.increment(); }

    public Snapshot snapshot() {
        return new Snapshot(value(metadataHits), value(metadataMisses), value(artifactHits),
                value(artifactMisses), value(artifactStores), value(coalescedJobs),
                value(aiLocal), value(aiOllama));
    }

    private long value(Counter counter) {
        return Math.round(counter.count());
    }

    public record Snapshot(
            long metadataHits,
            long metadataMisses,
            long artifactHits,
            long artifactMisses,
            long artifactStores,
            long coalescedJobs,
            long aiLocal,
            long aiOllama
    ) {}
}
