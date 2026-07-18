package uz.tubeforge.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.tubeforge.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
}
