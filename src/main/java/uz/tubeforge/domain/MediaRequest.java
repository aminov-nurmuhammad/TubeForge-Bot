package uz.tubeforge.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import uz.tubeforge.util.Sha256;

@Entity
@Table(name = "media_requests")
public class MediaRequest {
    @Id
    private String id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "preview_message_id")
    private Long previewMessageId;

    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "source_url_hash", length = 64)
    private String sourceUrlHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(name = "channel_name", columnDefinition = "TEXT")
    private String channelName;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "metadata_state", nullable = false)
    private MetadataState metadataState;

    @Column(name = "metadata_error_code", length = 64)
    private String metadataErrorCode;

    @Column(name = "metadata_error_message", columnDefinition = "TEXT")
    private String metadataErrorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "metadata_inspected_at", nullable = false)
    private Instant metadataInspectedAt;

    protected MediaRequest() {
    }

    public static MediaRequest inspecting(long userId, long chatId, String url, SourceType type,
                                          Instant now, Instant expiresAt) {
        var request = new MediaRequest();
        request.id = UUID.randomUUID().toString();
        request.telegramUserId = userId;
        request.chatId = chatId;
        request.sourceUrl = url;
        request.sourceUrlHash = Sha256.hex(url);
        request.sourceType = type;
        request.status = RequestStatus.INSPECTING;
        request.metadataState = MetadataState.PENDING;
        request.createdAt = now;
        request.expiresAt = expiresAt;
        request.metadataInspectedAt = now;
        return request;
    }

    public static MediaRequest instant(long userId, long chatId, String url, SourceType type,
                                       String metadataJson, String sourceId, String title,
                                       String channelName, Long durationSeconds, String thumbnailUrl,
                                       Instant now, Instant expiresAt) {
        MediaRequest request = inspecting(userId, chatId, url, type, now, expiresAt);
        request.metadataJson = metadataJson;
        request.sourceId = sourceId;
        request.title = title;
        request.channelName = channelName;
        request.durationSeconds = durationSeconds;
        request.thumbnailUrl = thumbnailUrl;
        request.status = RequestStatus.READY;
        return request;
    }

    public static MediaRequest cached(long userId, long chatId, String url, SourceType type,
                                      MediaRequest source, Instant now, Instant expiresAt) {
        MediaRequest request = inspecting(userId, chatId, url, type, now, expiresAt);
        request.sourceId = source.sourceId;
        request.title = source.title;
        request.channelName = source.channelName;
        request.durationSeconds = source.durationSeconds;
        request.thumbnailUrl = source.thumbnailUrl;
        request.metadataJson = source.metadataJson;
        request.metadataInspectedAt = source.metadataInspectedAt;
        request.sourceType = source.sourceType;
        request.status = RequestStatus.READY;
        request.metadataState = MetadataState.READY;
        return request;
    }

    public void ready(String sourceId, String title, String channelName, Long durationSeconds,
                      String thumbnailUrl, String metadataJson, SourceType type, Instant inspectedAt) {
        this.sourceId = sourceId;
        this.title = title;
        this.channelName = channelName;
        this.durationSeconds = durationSeconds;
        this.thumbnailUrl = thumbnailUrl;
        this.metadataJson = metadataJson;
        this.sourceType = type;
        this.metadataInspectedAt = inspectedAt;
        this.status = RequestStatus.READY;
        this.metadataState = MetadataState.READY;
        this.metadataErrorCode = null;
        this.metadataErrorMessage = null;
    }

    public void failed() { this.status = RequestStatus.FAILED; }
    public void metadataPending() {
        this.metadataState = MetadataState.PENDING;
        this.metadataErrorCode = null;
        this.metadataErrorMessage = null;
    }
    public void metadataDegraded(String code, String message, Instant inspectedAt) {
        this.metadataState = MetadataState.DEGRADED;
        this.metadataErrorCode = code;
        this.metadataErrorMessage = message;
        this.metadataInspectedAt = inspectedAt;
        this.status = RequestStatus.READY;
    }
    public void setPreviewMessageId(long messageId) { this.previewMessageId = messageId; }

    public String getId() { return id; }
    public Long getTelegramUserId() { return telegramUserId; }
    public Long getChatId() { return chatId; }
    public Long getPreviewMessageId() { return previewMessageId; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceUrlHash() { return sourceUrlHash; }
    public SourceType getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public String getTitle() { return title; }
    public String getChannelName() { return channelName; }
    public Long getDurationSeconds() { return durationSeconds; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getMetadataJson() { return metadataJson; }
    public RequestStatus getStatus() { return status; }
    public MetadataState getMetadataState() { return metadataState; }
    public String getMetadataErrorCode() { return metadataErrorCode; }
    public String getMetadataErrorMessage() { return metadataErrorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getMetadataInspectedAt() { return metadataInspectedAt; }
}
