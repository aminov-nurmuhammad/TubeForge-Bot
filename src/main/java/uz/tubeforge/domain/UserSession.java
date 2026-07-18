package uz.tubeforge.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "user_sessions")
public class UserSession {
    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Enumerated(EnumType.STRING)
    private SessionState state;

    @Column(name = "request_id")
    private String requestId;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected UserSession() {
    }

    public UserSession(long userId, SessionState state, String requestId, String payload, Instant expiresAt) {
        this.telegramUserId = userId;
        this.state = state;
        this.requestId = requestId;
        this.payload = payload;
        this.expiresAt = expiresAt;
    }

    public Long getTelegramUserId() { return telegramUserId; }
    public SessionState getState() { return state; }
    public String getRequestId() { return requestId; }
    public String getPayload() { return payload; }
    public Instant getExpiresAt() { return expiresAt; }
}
