package uz.tubeforge.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.tubeforge.domain.DownloadJob;
import uz.tubeforge.domain.JobStatus;
import uz.tubeforge.domain.JobType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DownloadJobRepository extends JpaRepository<DownloadJob, String> {
    long countByTelegramUserIdAndCreatedAtAfter(long userId, Instant after);
    List<DownloadJob> findByTelegramUserIdOrderByCreatedAtDesc(long userId, Pageable pageable);
    long countByStatusIn(Collection<JobStatus> statuses);
    List<DownloadJob> findByStatusIn(Collection<JobStatus> statuses);
    Optional<DownloadJob> findFirstByTelegramUserIdAndRequestIdAndJobTypeAndFormatCodeAndStatusInOrderByCreatedAtDesc(
            long userId, String requestId, JobType type, String formatCode, Collection<JobStatus> statuses);
}
