package uz.tubeforge.domain;

import jakarta.persistence.*;
import uz.tubeforge.telegram.DeliveryKind;
import uz.tubeforge.telegram.TelegramFileReference;

import java.time.Instant;

@Entity
@Table(name = "media_artifacts")
public class MediaArtifact {
    @Id
    @Column(name = "cache_key", length = 64)
    private String cacheKey;

    @Column(name = "source_id", nullable = false, length = 256)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 32)
    private JobType jobType;

    @Column(name = "format_code", length = 64)
    private String formatCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_kind", nullable = false, length = 16)
    private DeliveryKind deliveryKind;

    @Column(name = "telegram_file_id", nullable = false, columnDefinition = "TEXT")
    private String telegramFileId;

    @Column(name = "telegram_file_unique_id", columnDefinition = "TEXT")
    private String telegramFileUniqueId;

    @Column(name = "result_file_name", columnDefinition = "TEXT")
    private String resultFileName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "hit_count", nullable = false)
    private long hitCount;

    protected MediaArtifact() {
    }

    public static MediaArtifact create(String key, String sourceId, JobType type, String formatCode,
                                       TelegramFileReference reference, String fileName,
                                       Instant now, Instant expiresAt) {
        MediaArtifact artifact = new MediaArtifact();
        artifact.cacheKey = key;
        artifact.sourceId = sourceId;
        artifact.jobType = type;
        artifact.formatCode = formatCode;
        artifact.refresh(reference, fileName, now, expiresAt);
        artifact.createdAt = now;
        artifact.hitCount = 0;
        return artifact;
    }

    public void refresh(TelegramFileReference reference, String fileName, Instant now, Instant expiresAt) {
        this.deliveryKind = reference.kind();
        this.telegramFileId = reference.fileId();
        this.telegramFileUniqueId = reference.fileUniqueId();
        this.resultFileName = fileName;
        this.sizeBytes = reference.fileSize();
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
    }

    public void hit(Instant now) {
        hitCount++;
        lastUsedAt = now;
    }

    public String getCacheKey() { return cacheKey; }
    public String getSourceId() { return sourceId; }
    public JobType getJobType() { return jobType; }
    public String getFormatCode() { return formatCode; }
    public DeliveryKind getDeliveryKind() { return deliveryKind; }
    public String getTelegramFileId() { return telegramFileId; }
    public String getTelegramFileUniqueId() { return telegramFileUniqueId; }
    public String getResultFileName() { return resultFileName; }
    public Long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getHitCount() { return hitCount; }
}
