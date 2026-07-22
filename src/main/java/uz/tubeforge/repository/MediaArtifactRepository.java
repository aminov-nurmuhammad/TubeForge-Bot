package uz.tubeforge.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.tubeforge.domain.MediaArtifact;

import java.time.Instant;
import java.util.Optional;

public interface MediaArtifactRepository extends JpaRepository<MediaArtifact, String> {
    Optional<MediaArtifact> findByCacheKeyAndExpiresAtAfter(String cacheKey, Instant now);
    long deleteByExpiresAtBefore(Instant cutoff);

    @Query("select coalesce(sum(a.hitCount), 0) from MediaArtifact a")
    long totalHits();
}
