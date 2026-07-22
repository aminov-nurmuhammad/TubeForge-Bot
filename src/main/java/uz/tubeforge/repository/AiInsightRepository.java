package uz.tubeforge.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tubeforge.domain.AiInsight;

import java.time.Instant;
import java.util.Optional;

public interface AiInsightRepository extends JpaRepository<AiInsight, String> {
    Optional<AiInsight> findByCacheKeyAndExpiresAtAfter(String cacheKey, Instant now);
    long deleteByExpiresAtBefore(Instant cutoff);
}
