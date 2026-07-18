package uz.tubeforge.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected MediaRequest() {
    }

    public static MediaRequest inspecting(long userId, long chatId, String url, SourceType type,
                                          Instant now, Instant expiresAt) {
        var request = new MediaRequest();
        request.id = UUID.randomUUID().toString();
        request.telegramUserId = userId;
        request.chatId = chatId;
        request.sourceUrl = url;
        request.sourceType = type;
        request.status = RequestStatus.INSPECTING;
        request.createdAt = now;
        request.expiresAt = expiresAt;
        return request;
    }

    public void ready(String sourceId, String title, String channelName, Long durationSeconds,
                      String thumbnailUrl, String metadataJson, SourceType type) {
        this.sourceId = sourceId;
        this.title = title;
        this.channelName = channelName;
        this.durationSeconds = durationSeconds;
        this.thumbnailUrl = thumbnailUrl;
        this.metadataJson = metadataJson;
        this.sourceType = type;
        this.status = RequestStatus.READY;
    }

    public void failed() { this.status = RequestStatus.FAILED; }
    public void setPreviewMessageId(long messageId) { this.previewMessageId = messageId; }

    public String getId() { return id; }
    public Long getTelegramUserId() { return telegramUserId; }
    public Long getChatId() { return chatId; }
    public Long getPreviewMessageId() { return previewMessageId; }
    public String getSourceUrl() { return sourceUrl; }
    public SourceType getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public String getTitle() { return title; }
    public String getChannelName() { return channelName; }
    public Long getDurationSeconds() { return durationSeconds; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getMetadataJson() { return metadataJson; }
    public RequestStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
