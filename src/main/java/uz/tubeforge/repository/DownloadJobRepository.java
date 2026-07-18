package uz.tubeforge.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.tubeforge.domain.DownloadJob;
import uz.tubeforge.domain.JobStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DownloadJobRepository extends JpaRepository<DownloadJob, String> {
    long countByTelegramUserIdAndCreatedAtAfter(long userId, Instant after);
    List<DownloadJob> findByTelegramUserIdOrderByCreatedAtDesc(long userId, Pageable pageable);
    long countByStatusIn(Collection<JobStatus> statuses);
    List<DownloadJob> findByStatusIn(Collection<JobStatus> statuses);
}
