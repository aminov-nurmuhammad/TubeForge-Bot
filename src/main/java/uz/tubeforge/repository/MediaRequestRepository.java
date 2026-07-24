package uz.tubeforge.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.tubeforge.domain.MediaRequest;
import uz.tubeforge.domain.MetadataState;
import uz.tubeforge.domain.RequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MediaRequestRepository extends JpaRepository<MediaRequest, String> {
    List<MediaRequest> findByTelegramUserIdOrderByCreatedAtDesc(long userId, Pageable pageable);

    Optional<MediaRequest> findFirstByTelegramUserIdAndChatIdAndSourceUrlAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            long userId, long chatId, String sourceUrl, RequestStatus status, Instant expiresAt);

    Optional<MediaRequest> findFirstBySourceUrlHashAndStatusAndMetadataStateAndMetadataInspectedAtAfterOrderByCreatedAtDesc(
            String sourceUrlHash, RequestStatus status, MetadataState metadataState, Instant inspectedAfter);
}
