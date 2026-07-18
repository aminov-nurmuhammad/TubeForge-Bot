package uz.tubeforge.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tubeforge.domain.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
}
