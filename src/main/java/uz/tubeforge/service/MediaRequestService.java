package uz.tubeforge.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tubeforge.config.MediaProperties;
import uz.tubeforge.domain.MediaRequest;
import uz.tubeforge.domain.RequestStatus;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.media.ParsedYouTubeUrl;
import uz.tubeforge.repository.MediaRequestRepository;

import java.time.Clock;
import java.util.List;

@Service
public class MediaRequestService {
    private final MediaRequestRepository repository;
    private final MediaProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MediaRequestService(MediaRequestRepository repository, MediaProperties properties,
                               ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public MediaRequest create(long userId, long chatId, ParsedYouTubeUrl url) {
        MediaRequest request = MediaRequest.inspecting(userId, chatId, url.normalizedUrl(), url.sourceType(),
                clock.instant(), clock.instant().plus(properties.cacheRetention()));
        return repository.save(request);
    }

    @Transactional
    public MediaRequest markReady(String id, MediaInfo info) {
        MediaRequest request = requireOwnedOrSystem(id);
        try {
            request.ready(info.id(), info.title(), info.channel(), info.durationSeconds(), info.thumbnailUrl(),
                    objectMapper.writeValueAsString(info), info.sourceType());
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

    private MediaRequest requireOwnedOrSystem(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Media request was not found"));
    }
}
