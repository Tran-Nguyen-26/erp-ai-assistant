package vn.edu.uet.erpai.auth.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uet.erpai.auth.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  Optional<AppUser> findByUsername(String username);
}
