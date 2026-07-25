package uz.tubeforge.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tubeforge.config.MediaProperties;
import uz.tubeforge.domain.MediaRequest;
import uz.tubeforge.domain.MetadataState;
import uz.tubeforge.domain.RequestStatus;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.media.ParsedMediaUrl;
import uz.tubeforge.repository.MediaRequestRepository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import uz.tubeforge.util.Sha256;

@Service
public class MediaRequestService {
    private final MediaRequestRepository repository;
    private final MediaProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PerformanceMetrics metrics;

    public MediaRequestService(MediaRequestRepository repository, MediaProperties properties,
                               ObjectMapper objectMapper, Clock clock, PerformanceMetrics metrics) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public MediaRequest createInstant(long userId, long chatId, ParsedMediaUrl url) {
        MediaInfo info = MediaInfo.provisional(url);
        try {
            MediaRequest request = MediaRequest.instant(userId, chatId, url.normalizedUrl(), url.sourceType(),
                    objectMapper.writeValueAsString(info), info.id(), info.title(), info.channel(),
                    info.durationSeconds(), info.thumbnailUrl(), clock.instant(),
                    clock.instant().plus(properties.cacheRetention()));
            return repository.save(request);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not create the instant media preview", e);
        }
    }

    @Transactional
    public MediaRequest markReady(String id, MediaInfo info) {
        MediaRequest request = requireOwnedOrSystem(id);
        try {
            request.ready(info.id(), info.title(), info.channel(), info.durationSeconds(), info.thumbnailUrl(),
                    objectMapper.writeValueAsString(info), info.sourceType(), clock.instant());
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not store media information", e);
        }
        return repository.save(request);
    }

    @Transactional
    public void markFailed(String id) {
        MediaRequest request = requireOwnedOrSystem(id);
        request.failed();
        repository.save(request);
    }

    @Transactional
    public MediaRequest markMetadataPending(String id) {
        MediaRequest request = requireOwnedOrSystem(id);
        request.metadataPending();
        return repository.save(request);
    }

    @Transactional
    public MediaRequest markMetadataDegraded(String id, String code, String message) {
        MediaRequest request = requireOwnedOrSystem(id);
        request.metadataDegraded(code, safe(message), clock.instant());
        return repository.save(request);
    }

    @Transactional
    public void attachPreviewMessage(String id, long messageId) {
        MediaRequest request = requireOwnedOrSystem(id);
        request.setPreviewMessageId(messageId);
        repository.save(request);
    }

    @Transactional(readOnly = true)
    public MediaRequest requireOwned(String id, long userId) {
        MediaRequest request = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Media request was not found"));
        if (!request.getTelegramUserId().equals(userId)) throw new SecurityException("This request belongs to another user");
        if (request.getStatus() != RequestStatus.READY) throw new IllegalStateException("Media is not ready");
        if (request.getExpiresAt().isBefore(clock.instant())) throw new IllegalStateException("This media request has expired");
        return request;
    }

    @Transactional(readOnly = true)
    public MediaInfo info(MediaRequest request) {
        try {
            return objectMapper.readValue(request.getMetadataJson(), MediaInfo.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored media information is invalid", e);
        }
    }

    @Transactional(readOnly = true)
    public List<MediaRequest> recent(long userId, int limit) {
        return repository.findByTelegramUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
    }

    public boolean isUsable(MediaRequest request) {
        return request.getStatus() == RequestStatus.READY && request.getExpiresAt().isAfter(clock.instant());
    }

    @Transactional
    public Optional<MediaRequest> reusable(long userId, long chatId, ParsedMediaUrl url) {
        Optional<MediaRequest> source = repository.findFirstBySourceUrlHashAndStatusAndMetadataStateAndMetadataInspectedAtAfterOrderByCreatedAtDesc(
                Sha256.hex(url.normalizedUrl()), RequestStatus.READY, MetadataState.READY,
                clock.instant().minus(properties.cacheRetention()));
        if (source.isEmpty()) {
            metrics.metadataMiss();
            return Optional.empty();
        }
        MediaRequest copy = MediaRequest.cached(userId, chatId, url.normalizedUrl(), url.sourceType(),
                source.orElseThrow(), clock.instant(), clock.instant().plus(properties.cacheRetention()));
        metrics.metadataHit();
        return Optional.of(repository.save(copy));
    }

    private MediaRequest requireOwnedOrSystem(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Media request was not found"));
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "Metadata inspection was unavailable";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
