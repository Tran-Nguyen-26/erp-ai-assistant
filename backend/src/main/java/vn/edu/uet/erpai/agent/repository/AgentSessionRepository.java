package vn.edu.uet.erpai.agent.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uet.erpai.agent.domain.AgentSession;

public interface AgentSessionRepository extends JpaRepository<AgentSession, UUID> {

  Optional<AgentSession> findByIdAndUserId(UUID id, UUID userId);

  Page<AgentSession> findByUserId(UUID userId, Pageable pageable);
}
