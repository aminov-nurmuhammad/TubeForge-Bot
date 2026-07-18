package uz.tubeforge.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tubeforge.domain.SessionState;
import uz.tubeforge.domain.UserSession;
import uz.tubeforge.repository.UserSessionRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Service
public class SessionService {
    private final UserSessionRepository repository;
    private final Clock clock;

    public SessionService(UserSessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void awaitClipRange(long userId, String requestId, String payload) {
        repository.save(new UserSession(userId, SessionState.AWAITING_CLIP_RANGE, requestId, payload,
                clock.instant().plus(Duration.ofMinutes(10))));
    }

    @Transactional
    public void awaitTerms(long userId, String url) {
        repository.save(new UserSession(userId, SessionState.AWAITING_TERMS, null, url,
                clock.instant().plus(Duration.ofHours(1))));
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> active(long userId) {
        return repository.findById(userId).filter(session -> session.getExpiresAt().isAfter(clock.instant()));
    }

    @Transactional
    public void clear(long userId) {
        repository.deleteById(userId);
    }
}
