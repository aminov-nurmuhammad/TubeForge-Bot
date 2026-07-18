package uz.tubeforge.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Enumerated(EnumType.STRING)
    private Language language = Language.EN;

    @Column(name = "default_video_quality")
    private String defaultVideoQuality = "720";

    @Column(name = "default_audio_format")
    private String defaultAudioFormat = "MP3";

    @Column(name = "send_as_document")
    private boolean sendAsDocument;

    @Column(name = "auto_compress")
    private boolean autoCompress = true;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected AppUser() {
    }

    public AppUser(long telegramUserId, String username, String firstName, String lastName,
                   Language language, Instant now) {
        this.telegramUserId = telegramUserId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.language = language;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    public void touch(String username, String firstName, String lastName, Instant now) {
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.lastSeenAt = now;
    }

    public void acceptTerms(Instant now) { this.termsAcceptedAt = now; }
    public void setLanguage(Language language) { this.language = language; }
    public void setDefaultVideoQuality(String quality) { this.defaultVideoQuality = quality; }
    public void setDefaultAudioFormat(String format) { this.defaultAudioFormat = format; }
    public void setSendAsDocument(boolean value) { this.sendAsDocument = value; }
    public void setAutoCompress(boolean value) { this.autoCompress = value; }

    public Long getTelegramUserId() { return telegramUserId; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Language getLanguage() { return language; }
    public String getDefaultVideoQuality() { return defaultVideoQuality; }
    public String getDefaultAudioFormat() { return defaultAudioFormat; }
    public boolean isSendAsDocument() { return sendAsDocument; }
    public boolean isAutoCompress() { return autoCompress; }
    public Instant getTermsAcceptedAt() { return termsAcceptedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
