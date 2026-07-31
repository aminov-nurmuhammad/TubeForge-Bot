package uz.tubeforge.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tubeforge.config.CacheProperties;
import uz.tubeforge.domain.*;
import uz.tubeforge.media.MediaInfo;
import uz.tubeforge.repository.MediaArtifactRepository;
import uz.tubeforge.telegram.TelegramApiClient;
import uz.tubeforge.telegram.TelegramFileReference;
import uz.tubeforge.telegram.model.TgMessage;
import uz.tubeforge.telegram.model.InlineKeyboard;
import uz.tubeforge.util.Html;

import java.time.Clock;
import java.util.Optional;
import uz.tubeforge.util.Sha256;

@Service
public class ArtifactCacheService {
    private final MediaArtifactRepository repository;
    private final TelegramApiClient telegram;
    private final CacheProperties properties;
    private final PerformanceMetrics metrics;
    private final Clock clock;

    public ArtifactCacheService(MediaArtifactRepository repository, TelegramApiClient telegram,
                                CacheProperties properties, PerformanceMetrics metrics, Clock clock) {
        this.repository = repository;
        this.telegram = telegram;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public String key(MediaRequest request, DownloadJob job, AppUser user) {
        return key(request, job.getJobType(), job.getFormatCode(), user);
    }

    public String key(MediaRequest request, JobType type, String formatCode, AppUser user) {
        String identity = request.getSourceId() == null || request.getSourceId().isBlank()
                ? request.getSourceUrl() : request.getSourceId();
        boolean instantReel = request.getSourceType() == SourceType.INSTAGRAM_REEL && type == JobType.VIDEO;
        boolean document = instantReel ? false : user.isSendAsDocument();
        boolean compress = instantReel || user.isAutoCompress();
        String raw = request.getSourceType() + "|" + identity + '|' + type + '|' + nullToEmpty(formatCode)
                + "|document=" + document + "|compress=" + compress;
        return Sha256.hex(raw);
    }

    public boolean isAvailable(String key) {
        return properties.enabled() && repository.findByCacheKeyAndExpiresAtAfter(key, clock.instant()).isPresent();
    }

    public boolean cacheable(JobType type) {
        return properties.enabled() && switch (type) {
            case VIDEO, AUDIO, THUMBNAIL, SUBTITLES, TRANSCRIPT, CLIP_VIDEO, CLIP_AUDIO -> true;
            default -> false;
        };
    }

    @Transactional
    public Optional<MediaArtifact> find(String key) {
        if (!properties.enabled()) return Optional.empty();
        Optional<MediaArtifact> artifact = repository.findByCacheKeyAndExpiresAtAfter(key, clock.instant());
        if (artifact.isPresent()) {
            artifact.orElseThrow().hit(clock.instant());
            metrics.artifactHit();
        } else {
            metrics.artifactMiss();
        }
        return artifact;
    }

    @Transactional
    public Optional<MediaArtifact> store(String key, MediaRequest request, DownloadJob job,
                                         MediaDeliveryService.DeliveryResult delivery) {
        if (!cacheable(job.getJobType())) return Optional.empty();
        Optional<TelegramFileReference> reference = delivery.singleReference();
        if (reference.isEmpty()) return Optional.empty();
        String sourceId = request.getSourceId() == null || request.getSourceId().isBlank()
                ? request.getSourceUrl() : request.getSourceId();
        String fileName = delivery.files().isEmpty() ? job.getResultFileName()
                : delivery.files().get(0).getFileName().toString();
        var existing = repository.findById(key);
        MediaArtifact artifact = existing.orElseGet(() -> MediaArtifact.create(key, sourceId, job.getJobType(),
                job.getFormatCode(), reference.orElseThrow(), fileName, clock.instant(),
                clock.instant().plus(properties.artifactRetention())));
        if (existing.isPresent()) {
            artifact.refresh(reference.orElseThrow(), fileName, clock.instant(),
                    clock.instant().plus(properties.artifactRetention()));
        }
        MediaArtifact saved = repository.save(artifact);
        metrics.artifactStore();
        return Optional.of(saved);
    }

    public TelegramFileReference deliver(MediaArtifact artifact, long chatId, MediaInfo info) {
        return deliver(artifact, chatId, info, null);
    }

    public TelegramFileReference deliver(MediaArtifact artifact, long chatId, MediaInfo info,
                                         InlineKeyboard keyboard) {
        String caption = info.sourceType() == SourceType.INSTAGRAM_REEL
                ? "📸 <b>Instagram Reel</b>"
                : "⚡ <b>TubeForge</b>\n" + Html.escape(info.title());
        TgMessage message = switch (artifact.getDeliveryKind()) {
            case VIDEO -> keyboard == null
                    ? telegram.sendVideo(chatId, artifact.getTelegramFileId(), caption, true)
                    : telegram.sendVideo(chatId, artifact.getTelegramFileId(), caption, true, keyboard);
            case AUDIO -> keyboard == null
                    ? telegram.sendAudio(chatId, artifact.getTelegramFileId(), caption, info.title(), info.channel())
                    : telegram.sendAudio(chatId, artifact.getTelegramFileId(), caption, info.title(), info.channel(), keyboard);
            case PHOTO -> keyboard == null
                    ? telegram.sendPhoto(chatId, artifact.getTelegramFileId(), caption)
                    : telegram.sendPhoto(chatId, artifact.getTelegramFileId(), caption, keyboard);
            case DOCUMENT -> keyboard == null
                    ? telegram.sendDocument(chatId, artifact.getTelegramFileId(), caption)
                    : telegram.sendDocument(chatId, artifact.getTelegramFileId(), caption, keyboard);
        };
        return TelegramFileReference.from(message).orElse(new TelegramFileReference(
                artifact.getDeliveryKind(), artifact.getTelegramFileId(), artifact.getTelegramFileUniqueId(),
                artifact.getSizeBytes() == null ? 0 : artifact.getSizeBytes()));
    }

    @Transactional
    public void invalidate(String key) {
        repository.deleteById(key);
    }

    public long entries() { return repository.count(); }
    public long hits() { return repository.totalHits(); }

    @Scheduled(fixedDelayString = "PT6H")
    @Transactional
    public void removeExpired() {
        repository.deleteByExpiresAtBefore(clock.instant());
    }

    private String nullToEmpty(String value) { return value == null ? "" : value; }
}
