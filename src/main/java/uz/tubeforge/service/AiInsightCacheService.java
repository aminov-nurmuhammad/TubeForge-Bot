package uz.tubeforge.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tubeforge.ai.AiInsightResult;
import uz.tubeforge.ai.InsightType;
import uz.tubeforge.config.AiProperties;
import uz.tubeforge.config.CacheProperties;
import uz.tubeforge.domain.*;
import uz.tubeforge.repository.AiInsightRepository;

import java.time.Clock;
import java.util.Optional;
import uz.tubeforge.util.Sha256;

@Service
public class AiInsightCacheService {
    private final AiInsightRepository repository;
    private final CacheProperties cacheProperties;
    private final AiProperties aiProperties;
    private final Clock clock;

    public AiInsightCacheService(AiInsightRepository repository, CacheProperties cacheProperties,
                                 AiProperties aiProperties, Clock clock) {
        this.repository = repository;
        this.cacheProperties = cacheProperties;
        this.aiProperties = aiProperties;
        this.clock = clock;
    }

    public String key(MediaRequest request, DownloadJob job, AppUser user, InsightType type) {
        String identity = request.getSourceId() == null || request.getSourceId().isBlank()
                ? request.getSourceUrl() : request.getSourceId();
        return Sha256.hex(identity + '|' + type + '|' + job.getFormatCode() + '|' + user.getLanguage()
                + '|' + aiProperties.provider() + '|' + aiProperties.model());
    }

    @Transactional
    public Optional<AiInsight> find(String key) {
        if (!cacheProperties.enabled()) return Optional.empty();
        Optional<AiInsight> insight = repository.findByCacheKeyAndExpiresAtAfter(key, clock.instant());
        insight.ifPresent(value -> value.hit(clock.instant()));
        return insight;
    }

    @Transactional
    public AiInsight store(String key, MediaRequest request, DownloadJob job, AppUser user,
                           InsightType type, AiInsightResult result) {
        String sourceId = request.getSourceId() == null || request.getSourceId().isBlank()
                ? request.getSourceUrl() : request.getSourceId();
        AiInsight insight = AiInsight.create(key, sourceId, type, transcriptLanguage(job), user.getLanguage(),
                result.content(), result.provider(), clock.instant(),
                clock.instant().plus(cacheProperties.insightRetention()));
        return repository.save(insight);
    }

    public long entries() { return repository.count(); }

    @Scheduled(fixedDelayString = "PT6H")
    @Transactional
    public void removeExpired() { repository.deleteByExpiresAtBefore(clock.instant()); }

    private String transcriptLanguage(DownloadJob job) {
        String format = job.getFormatCode();
        int at = format == null ? -1 : format.indexOf('@');
        return at < 0 ? (format == null ? "unknown" : format) : format.substring(0, at);
    }

}
