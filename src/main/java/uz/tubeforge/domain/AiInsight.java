package uz.tubeforge.domain;

import jakarta.persistence.*;
import uz.tubeforge.ai.InsightType;

import java.time.Instant;

@Entity
@Table(name = "ai_insights")
public class AiInsight {
    @Id
    @Column(name = "cache_key", length = 64)
    private String cacheKey;

    @Column(name = "source_id", nullable = false, length = 256)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "insight_type", nullable = false, length = 32)
    private InsightType insightType;

    @Column(name = "transcript_language", nullable = false, length = 32)
    private String transcriptLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_language", nullable = false, length = 8)
    private Language outputLanguage;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "hit_count", nullable = false)
    private long hitCount;

    protected AiInsight() {
    }

    public static AiInsight create(String key, String sourceId, InsightType type, String transcriptLanguage,
                                   Language outputLanguage, String content, String provider,
                                   Instant now, Instant expiresAt) {
        AiInsight insight = new AiInsight();
        insight.cacheKey = key;
        insight.sourceId = sourceId;
        insight.insightType = type;
        insight.transcriptLanguage = transcriptLanguage;
        insight.outputLanguage = outputLanguage;
        insight.content = content;
        insight.provider = provider;
        insight.createdAt = now;
        insight.lastUsedAt = now;
        insight.expiresAt = expiresAt;
        return insight;
    }

    public void hit(Instant now) { hitCount++; lastUsedAt = now; }

    public String getCacheKey() { return cacheKey; }
    public String getSourceId() { return sourceId; }
    public InsightType getInsightType() { return insightType; }
    public String getTranscriptLanguage() { return transcriptLanguage; }
    public Language getOutputLanguage() { return outputLanguage; }
    public String getContent() { return content; }
    public String getProvider() { return provider; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getHitCount() { return hitCount; }
}
