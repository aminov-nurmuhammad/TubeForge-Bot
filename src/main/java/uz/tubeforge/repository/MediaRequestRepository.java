package uz.tubeforge.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.tubeforge.domain.MediaRequest;

import java.util.List;

public interface MediaRequestRepository extends JpaRepository<MediaRequest, String> {
    List<MediaRequest> findByTelegramUserIdOrderByCreatedAtDesc(long userId, Pageable pageable);
}
